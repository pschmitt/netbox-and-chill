package dev.pschmitt.nyetbox.data.db

import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Keeps the existing DAO-shaped repository APIs while making Flow reads follow a server switch.
 * Suspend and one-shot DAO methods delegate to whichever database is active at call time.
 */
object DynamicDaoProxy {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(
        daoClass: Class<T>,
        databaseManager: CacheDatabaseManager,
        selector: (AppDatabase) -> T,
    ): T =
        Proxy.newProxyInstance(daoClass.classLoader, arrayOf(daoClass)) { _, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance when (method.name) {
                    "toString" -> "DynamicDao(${daoClass.simpleName})"
                    "hashCode" -> System.identityHashCode(daoClass)
                    "equals" -> args?.firstOrNull() === daoClass
                    else -> null
                }
            }
            val invocationArgs = args ?: emptyArray()
            if (Flow::class.java.isAssignableFrom(method.returnType)) {
                return@newProxyInstance databaseManager.activeDatabase.flatMapLatest { database ->
                    @Suppress("UNCHECKED_CAST")
                    method.invoke(selector(database), *invocationArgs) as Flow<Any?>
                }
            }
            method.invoke(selector(databaseManager.activeDatabase.value), *invocationArgs)
        } as T
}
