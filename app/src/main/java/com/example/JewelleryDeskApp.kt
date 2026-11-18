package com.example

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.JewelleryDatabase
import com.example.data.remote.SawariyaSethProductService
import com.example.data.repository.JewelleryRepository
import com.example.ui.screens.BillsViewModel
import com.example.ui.screens.CustomersViewModel
import com.example.ui.screens.DashboardViewModel
import com.example.ui.screens.InventoryViewModel
import com.example.ui.screens.NewBillViewModel
import com.example.ui.screens.PaymentsViewModel
import com.example.ui.screens.ReportsViewModel
import com.example.ui.screens.SettingsViewModel

class AppContainer(application: Application) {
    val database: JewelleryDatabase by lazy { JewelleryDatabase.create(application) }
    val productService: SawariyaSethProductService by lazy { SawariyaSethProductService() }
    val repository: JewelleryRepository by lazy {
        JewelleryRepository(database = database, productService = productService)
    }

    val viewModelFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                        DashboardViewModel(repository) as T
                    modelClass.isAssignableFrom(NewBillViewModel::class.java) ->
                        NewBillViewModel(repository) as T
                    modelClass.isAssignableFrom(InventoryViewModel::class.java) ->
                        InventoryViewModel(repository) as T
                    modelClass.isAssignableFrom(CustomersViewModel::class.java) ->
                        CustomersViewModel(repository) as T
                    modelClass.isAssignableFrom(BillsViewModel::class.java) ->
                        BillsViewModel(repository) as T
                    modelClass.isAssignableFrom(PaymentsViewModel::class.java) ->
                        PaymentsViewModel(repository) as T
                    modelClass.isAssignableFrom(ReportsViewModel::class.java) ->
                        ReportsViewModel(repository) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                        SettingsViewModel(repository) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

class JewelleryDeskApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
