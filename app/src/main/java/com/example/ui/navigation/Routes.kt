package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data class NewBillRoute(val preselectedCustomerId: Long? = null)

@Serializable
data object InventoryRoute

@Serializable
data class InventoryDetailRoute(val itemId: Long)

@Serializable
data object CustomersRoute

@Serializable
data class CustomerDetailRoute(val customerId: Long)

@Serializable
data object BillsRoute

@Serializable
data class BillDetailRoute(val billId: Long)

@Serializable
data object PaymentsRoute

@Serializable
data object ReportsRoute

@Serializable
data object SettingsRoute
