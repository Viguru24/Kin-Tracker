package com.example

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.ui.FamilyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShoppingListTest {

    @Test
    fun testShoppingListOperationsAndSeeding() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        
        val application = ApplicationProvider.getApplicationContext<Application>()
        
        // Enable simulation mode in shared preferences so ensureDefaultDataInserted is triggered
        application.getSharedPreferences("kintracker_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("isSimulationModeEnabled", true)
            .apply()

        val viewModel = FamilyViewModel(application)
        
        try {
            // 1. Verify default shopping list seeds are loaded (wait for insertion)
            val items = viewModel.shoppingItems.first { it.size == 3 }
            assertTrue(items.any { it.name.contains("Fresh Milk") && it.addedByMemberId == "annette" })
            assertTrue(items.any { it.name.contains("Sourdough Bread") && it.addedByMemberId == "me" })
            assertTrue(items.any { it.name.contains("Ice Cream") && it.addedByMemberId == "eloise" })

            // 2. Test Add Item
            viewModel.addShoppingItem("Apples 🍎", "me", "Louis")
            val itemsAfterAdd = viewModel.shoppingItems.first { itemsList -> itemsList.any { it.name == "Apples 🍎" } }
            val apples = itemsAfterAdd.first { it.name == "Apples 🍎" }
            assertFalse(apples.isChecked)
            assertEquals("me", apples.addedByMemberId)
            
            // Verify Activity Log was written for add
            val logsAfterAdd = viewModel.activityLogs.first { logs -> 
                logs.any { it.actionText.contains("added 'Apples 🍎' to the shopping list") }
            }
            assertNotNull(logsAfterAdd)

            // 3. Test Toggle Item (Check off)
            viewModel.toggleShoppingItem(apples)
            val itemsAfterToggle = viewModel.shoppingItems.first { itemsList -> itemsList.any { it.name == "Apples 🍎" && it.isChecked } }
            val applesToggled = itemsAfterToggle.first { it.name == "Apples 🍎" }
            assertTrue(applesToggled.isChecked)

            // Verify Activity Log was written for toggle
            val logsAfterToggle = viewModel.activityLogs.first { logs ->
                logs.any { it.actionText.contains("marked 'Apples 🍎' as purchased") }
            }
            assertNotNull(logsAfterToggle)

            // 4. Test Delete Item
            viewModel.deleteShoppingItem(applesToggled)
            val itemsAfterDelete = viewModel.shoppingItems.first { itemsList -> itemsList.none { it.name == "Apples 🍎" } }
            assertEquals(3, itemsAfterDelete.size)

            // Verify Activity Log was written for delete
            val logsAfterDelete = viewModel.activityLogs.first { logs ->
                logs.any { it.actionText.contains("removed 'Apples 🍎' from the shopping list") }
            }
            assertNotNull(logsAfterDelete)
        } finally {
            // Cancel background viewModel coroutines to prevent UncompletedCoroutinesError
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
