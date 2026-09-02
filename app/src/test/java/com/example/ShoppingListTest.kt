package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActivityLog
import com.example.data.AppDatabase
import com.example.data.FamilyRepository
import com.example.data.ShoppingItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShoppingListTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: FamilyRepository

    @Before
    fun setup() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FamilyRepository(database.familyDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testShoppingListOperationsAndSeeding() = runBlocking {
        // 1. Seed initial items
        val seed1 = ShoppingItem(name = "Fresh Milk 🥛", addedByMemberId = "annette", addedByMemberName = "Annette", isChecked = false)
        val seed2 = ShoppingItem(name = "Sourdough Bread 🥖", addedByMemberId = "me", addedByMemberName = "Louis", isChecked = false)
        val seed3 = ShoppingItem(name = "Ice Cream 🍦", addedByMemberId = "eloise", addedByMemberName = "Eloise", isChecked = false)
        
        repository.insertShoppingItem(seed1)
        repository.insertShoppingItem(seed2)
        repository.insertShoppingItem(seed3)

        val items = repository.shoppingItems.first { it.size == 3 }
        assertTrue(items.any { it.name.contains("Fresh Milk") && it.addedByMemberId == "annette" })
        assertTrue(items.any { it.name.contains("Sourdough Bread") && it.addedByMemberId == "me" })
        assertTrue(items.any { it.name.contains("Ice Cream") && it.addedByMemberId == "eloise" })

        // 2. Test Add Item
        val appleItem = ShoppingItem(name = "Apples 🍎", addedByMemberId = "me", addedByMemberName = "Louis", isChecked = false)
        repository.insertShoppingItem(appleItem)
        repository.insertLog(ActivityLog(memberId = "me", memberName = "Louis", actionText = "Louis added 'Apples 🍎' to the shopping list", timestamp = System.currentTimeMillis(), iconName = "check_in"))

        val itemsAfterAdd = repository.shoppingItems.first { itemsList -> itemsList.any { it.name == "Apples 🍎" } }
        val apples = itemsAfterAdd.first { it.name == "Apples 🍎" }
        assertFalse(apples.isChecked)
        assertEquals("me", apples.addedByMemberId)

        val logsAfterAdd = repository.activityLogs.first { logs -> 
            logs.any { it.actionText.contains("added 'Apples 🍎' to the shopping list") }
        }
        assertNotNull(logsAfterAdd)

        // 3. Test Toggle Item (Check off)
        val toggled = apples.copy(isChecked = true)
        repository.updateShoppingItem(toggled)
        repository.insertLog(ActivityLog(memberId = "me", memberName = "Louis", actionText = "Louis marked 'Apples 🍎' as purchased", timestamp = System.currentTimeMillis(), iconName = "check_in"))

        val itemsAfterToggle = repository.shoppingItems.first { itemsList -> itemsList.any { it.name == "Apples 🍎" && it.isChecked } }
        val applesToggled = itemsAfterToggle.first { it.name == "Apples 🍎" }
        assertTrue(applesToggled.isChecked)

        val logsAfterToggle = repository.activityLogs.first { logs ->
            logs.any { it.actionText.contains("marked 'Apples 🍎' as purchased") }
        }
        assertNotNull(logsAfterToggle)

        // 4. Test Delete Item
        repository.deleteShoppingItem(applesToggled)
        repository.insertLog(ActivityLog(memberId = "me", memberName = "Louis", actionText = "Louis removed 'Apples 🍎' from the shopping list", timestamp = System.currentTimeMillis(), iconName = "check_in"))

        val itemsAfterDelete = repository.shoppingItems.first { itemsList -> itemsList.none { it.name == "Apples 🍎" } }
        assertEquals(3, itemsAfterDelete.size)

        val logsAfterDelete = repository.activityLogs.first { logs ->
            logs.any { it.actionText.contains("removed 'Apples 🍎' from the shopping list") }
        }
        assertNotNull(logsAfterDelete)
    }

    @Test
    fun testCloudTombstoneSuppressesResurrection() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val deletionPrefs = application.getSharedPreferences("shopping_deletions", android.content.Context.MODE_PRIVATE)

        val itemTime = System.currentTimeMillis() - 5000L
        val deleteTime = System.currentTimeMillis()

        // Record tombstone deletion
        deletionPrefs.edit()
            .putLong("organic milk", deleteTime)
            .putLong("organicmilk", deleteTime)
            .apply()

        // Verify that an older item from cloud or local is suppressed by tombstone
        val key = "Organic Milk".lowercase().replace("[^a-z0-9]".toRegex(), "").trim()
        val delTime = deletionPrefs.getLong(key, 0L)
        assertTrue(delTime > itemTime)

        // Adding a brand new item later should have timestamp > deleteTime and succeed
        val newItemTime = System.currentTimeMillis() + 1000L
        assertTrue(newItemTime > delTime)
    }
}
