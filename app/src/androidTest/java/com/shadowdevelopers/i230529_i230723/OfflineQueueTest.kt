package com.shadowdevelopers.i230529_i230723

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shadowdevelopers.i230529_i230723.data.OfflineStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineQueueTest {
    @Test fun restartPreservesOrderAndNewerOptimisticEdit() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val account=UUID.randomUUID().toString()
        var store=OfflineStore(context)
        try {
            store.enqueue(account,"message","m","original","message.send","{}")
            store.enqueue(account,"message","m","newer edit","message.edit","{}")
            store.close();store=OfflineStore(context)
            val first=store.claim(account)!!
            assertEquals("message.send",first.operation)
            assertNull(store.claim(account)) // Another drainer cannot claim the in-flight head.
            store.acknowledge(first,"message","server original")
            assertEquals(listOf("newer edit"),store.entities(account,"message"))
            val second=store.claim(account)!!
            assertEquals("message.edit",second.operation)
            store.acknowledge(second,"message","server edited")
            assertEquals(listOf("server edited"),store.entities(account,"message"))
            assertFalse(store.hasRetryableActions(account))
        } finally {store.clearAccount(account);store.close()}
    }
    @Test fun retryKeepsUuidAndCannotLeakAcrossAccounts() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val account=UUID.randomUUID().toString();val other=UUID.randomUUID().toString()
        val store=OfflineStore(context)
        try {
            val uuid=store.enqueue(account,"post","p","pending post","post.create","{}")
            val action=store.claim(account)!!
            store.fail(action,"network timeout",0)
            assertEquals(uuid,store.claim(account)!!.uuid)
            assertNull(store.claim(other))
            assertTrue(store.entities(other,"post").isEmpty())
            store.clearAccount(account)
            assertFalse(store.hasRetryableActions(account))
            assertTrue(store.entities(account,"post").isEmpty())
        } finally {store.clearAccount(account);store.close()}
    }
}
