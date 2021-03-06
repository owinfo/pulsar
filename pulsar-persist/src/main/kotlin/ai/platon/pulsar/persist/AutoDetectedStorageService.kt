package ai.platon.pulsar.persist

import ai.platon.pulsar.common.AppRuntime
import ai.platon.pulsar.common.config.AppConstants.*
import ai.platon.pulsar.common.config.CapabilityTypes.STORAGE_DATA_STORE_CLASS
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.persist.gora.GoraStorage
import ai.platon.pulsar.persist.gora.generated.GWebPage
import org.apache.gora.persistency.Persistent
import org.apache.gora.store.DataStore
import org.apache.hadoop.conf.Configuration
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by vincent on 19-1-19.
 * Copyright @ 2013-2019 Platon AI. All rights reserved
 *
 */
class AutoDetectedStorageService private constructor(conf: ImmutableConfig): AutoCloseable {
    private val log = LoggerFactory.getLogger(AutoDetectedStorageService::class.java)

    val storeClassName: String = detectDataStoreClassName(conf)
    val pageStoreClass: Class<out DataStore<String, GWebPage>> = detectDataStoreClass(conf)
    val pageStore: DataStore<String, GWebPage> = GoraStorage.createDataStore(conf.unbox(), String::class.java, GWebPage::class.java, pageStoreClass)
    val isClosed = AtomicBoolean()

    override fun close() {
        if (isClosed.getAndSet(true)) {
            return
        }

        pageStore.close()
    }

    companion object {

        private var instance: AutoDetectedStorageService? = null

        // TODO: in MapR, should be initialized after Job/Map/Reduer initialization since crawlId can be passed from command line
        fun create(initializeConf: ImmutableConfig): AutoDetectedStorageService {
            if (instance == null) {
                instance = AutoDetectedStorageService(initializeConf)
            }
            return instance!!
        }

        /**
         * Return the DataStore persistent class used to persist WebPage.
         *
         * @param conf AppConstants configuration
         * @return the DataStore persistent class
         */
        fun detectDataStoreClassName(conf: ImmutableConfig): String {
            return when {
                conf.isDryRun -> MEM_STORE_CLASS
                conf.isDistributedFs -> conf.get(STORAGE_DATA_STORE_CLASS, HBASE_STORE_CLASS)
                AppRuntime.checkIfProcessRunning(".+HMaster.+") ->
                    conf.get(STORAGE_DATA_STORE_CLASS, HBASE_STORE_CLASS)
                AppRuntime.checkIfProcessRunning(".+/usr/bin/mongod .+") ->
                    conf.get(STORAGE_DATA_STORE_CLASS, MONGO_STORE_CLASS)
                AppRuntime.checkIfProcessRunning(".+/tmp/.+extractmongod .+") ->
                    conf.get(STORAGE_DATA_STORE_CLASS, MONGO_STORE_CLASS)
                else -> MEM_STORE_CLASS
            }
        }

        /**
         * Return the DataStore persistent class used to persist WebPage.
         *
         * @param conf AppConstants configuration
         * @return the DataStore persistent class
         */
        @Throws(ClassNotFoundException::class)
        fun <K, V : Persistent> detectDataStoreClass(conf: ImmutableConfig): Class<out DataStore<K, V>> {
            return Class.forName(detectDataStoreClassName(conf)) as Class<out DataStore<K, V>>
        }

        @Throws(ClassNotFoundException::class)
        fun <K, V : Persistent> detectDataStoreClass(conf: Configuration): Class<out DataStore<K, V>> {
            return detectDataStoreClass(ImmutableConfig(conf))
        }
    }
}
