package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.navigation.BillDetailRoute
import com.example.ui.navigation.BillsRoute
import com.example.ui.navigation.CustomerDetailRoute
import com.example.ui.navigation.CustomersRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.InventoryDetailRoute
import com.example.ui.navigation.InventoryRoute
import com.example.ui.navigation.NewBillRoute
import com.example.ui.navigation.PaymentsRoute
import com.example.ui.navigation.ReportsRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.BillDetailScreen
import com.example.ui.screens.BillsHistoryScreen
import com.example.ui.screens.BillsViewModel
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.CustomersViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DashboardViewModel
import com.example.ui.screens.InventoryDetailScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.InventoryViewModel
import com.example.ui.screens.NewBillScreen
import com.example.ui.screens.NewBillViewModel
import com.example.ui.screens.PaymentsScreen
import com.example.ui.screens.PaymentsViewModel
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.ReportsViewModel
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SettingsViewModel
import com.example.ui.theme.JewelleryDeskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as JewelleryDeskApp
        setContent {
            JewelleryDeskTheme {
                JewelleryDeskRoot(appContainer = app.container)
            }
        }
    }
}

private data class NavSectionItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JewelleryDeskRoot(appContainer: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var showMoreSheet by remember { mutableStateOf(false) }

    val dashboardVm: DashboardViewModel = viewModel(factory = appContainer.viewModelFactory)
    val newBillVm: NewBillViewModel = viewModel(factory = appContainer.viewModelFactory)
    val inventoryVm: InventoryViewModel = viewModel(factory = appContainer.viewModelFactory)
    val customersVm: CustomersViewModel = viewModel(factory = appContainer.viewModelFactory)
    val billsVm: BillsViewModel = viewModel(factory = appContainer.viewModelFactory)
    val paymentsVm: PaymentsViewModel = viewModel(factory = appContainer.viewModelFactory)
    val reportsVm: ReportsViewModel = viewModel(factory = appContainer.viewModelFactory)
    val settingsVm: SettingsViewModel = viewModel(factory = appContainer.viewModelFactory)

    val allSections = remember {
        listOf(
            NavSectionItem("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, DashboardRoute, "nav_dashboard"),
            NavSectionItem("New Bill", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, NewBillRoute(), "nav_new_bill"),
            NavSectionItem("Inventory", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, InventoryRoute, "nav_inventory"),
            NavSectionItem("Customers", Icons.Filled.Storefront, Icons.Outlined.Storefront, CustomersRoute, "nav_customers"),
            NavSectionItem("Bills", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, BillsRoute, "nav_bills"),
            NavSectionItem("Payments", Icons.Filled.Payments, Icons.Outlined.Payments, PaymentsRoute, "nav_payments"),
            NavSectionItem("Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart, ReportsRoute, "nav_reports"),
            NavSectionItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, SettingsRoute, "nav_settings")
        )
    }

    val isDetailRoute = currentDestination?.hasRoute<CustomerDetailRoute>() == true ||
        currentDestination?.hasRoute<BillDetailRoute>() == true ||
        currentDestination?.hasRoute<InventoryDetailRoute>() == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = route !is NewBillRoute
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Business Modules",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                listOf(allSections[4], allSections[5], allSections[6], allSections[7]).forEach { section ->
                    androidx.compose.material3.Surface(
                        onClick = {
                            showMoreSheet = false
                            navigateTopLevel(section.route)
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("sheet_${section.testTag}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = section.unselectedIcon,
                                contentDescription = section.label,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 10.dp))
                            Text(section.label, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useSidebar = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useSidebar && !isDetailRoute) {
                    val primaryTabs = allSections.take(4)
                    val moreSelected = currentDestination?.hasRoute<BillsRoute>() == true ||
                        currentDestination?.hasRoute<PaymentsRoute>() == true ||
                        currentDestination?.hasRoute<ReportsRoute>() == true ||
                        currentDestination?.hasRoute<SettingsRoute>() == true

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryTabs.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        NavigationBarItem(
                            selected = moreSelected,
                            onClick = { showMoreSheet = true },
                            icon = {
                                Icon(Icons.Filled.MoreHoriz, contentDescription = "More")
                            },
                            label = { Text("More") },
                            modifier = Modifier.testTag("nav_more")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useSidebar && !isDetailRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        allSections.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                viewModel = dashboardVm,
                                onCreateNewBill = { navigateTopLevel(NewBillRoute()) },
                                onOpenInventory = { navigateTopLevel(InventoryRoute) },
                                onAddCustomer = { navigateTopLevel(CustomersRoute) },
                                onRecordPayment = { navigateTopLevel(PaymentsRoute) },
                                onViewBills = { navigateTopLevel(BillsRoute) },
                                onOpenBillDetail = { billId ->
                                    navController.navigate(BillDetailRoute(billId))
                                },
                                onOpenReports = { navigateTopLevel(ReportsRoute) },
                                onOpenSettings = { navigateTopLevel(SettingsRoute) },
                                showTopSecondaryShortcuts = !useSidebar
                            )
                        }

                        composable<NewBillRoute> { entry ->
                            val args = entry.toRoute<NewBillRoute>()
                            NewBillScreen(
                                viewModel = newBillVm,
                                preselectedCustomerId = args.preselectedCustomerId,
                                onBillSaved = { savedId ->
                                    navController.navigate(BillDetailRoute(savedId))
                                },
                                onOpenInventoryItem = { invId ->
                                    navController.navigate(InventoryDetailRoute(invId))
                                }
                            )
                        }

                        composable<InventoryRoute> {
                            InventoryScreen(
                                viewModel = inventoryVm,
                                onOpenInventoryDetail = { itemId ->
                                    navController.navigate(InventoryDetailRoute(itemId))
                                }
                            )
                        }

                        composable<InventoryDetailRoute> { entry ->
                            val args = entry.toRoute<InventoryDetailRoute>()
                            InventoryDetailScreen(
                                itemId = args.itemId,
                                viewModel = inventoryVm,
                                onBack = { navController.popBackStack() },
                                onOpenBillDetail = { billId ->
                                    navController.navigate(BillDetailRoute(billId))
                                }
                            )
                        }

                        composable<CustomersRoute> {
                            CustomersScreen(
                                viewModel = customersVm,
                                onOpenCustomerDetail = { customerId ->
                                    navController.navigate(CustomerDetailRoute(customerId))
                                }
                            )
                        }

                        composable<CustomerDetailRoute> { entry ->
                            val args = entry.toRoute<CustomerDetailRoute>()
                            CustomerDetailScreen(
                                customerId = args.customerId,
                                viewModel = customersVm,
                                onBack = { navController.popBackStack() },
                                onCreateBillForCustomer = { custId ->
                                    navController.navigate(NewBillRoute(preselectedCustomerId = custId))
                                },
                                onOpenBillDetail = { billId ->
                                    navController.navigate(BillDetailRoute(billId))
                                }
                            )
                        }

                        composable<BillsRoute> {
                            BillsHistoryScreen(
                                viewModel = billsVm,
                                onOpenBillDetail = { billId ->
                                    navController.navigate(BillDetailRoute(billId))
                                },
                                onCreateNewBill = { navigateTopLevel(NewBillRoute()) }
                            )
                        }

                        composable<BillDetailRoute> { entry ->
                            val args = entry.toRoute<BillDetailRoute>()
                            BillDetailScreen(
                                billId = args.billId,
                                viewModel = billsVm,
                                onBack = { navController.popBackStack() },
                                onOpenCustomer = { custId ->
                                    navController.navigate(CustomerDetailRoute(custId))
                                },
                                onOpenInventoryItem = { invId ->
                                    navController.navigate(InventoryDetailRoute(invId))
                                }
                            )
                        }

                        composable<PaymentsRoute> {
                            PaymentsScreen(
                                viewModel = paymentsVm,
                                onOpenCustomer = { custId ->
                                    navController.navigate(CustomerDetailRoute(custId))
                                },
                                onOpenBill = { billId ->
                                    navController.navigate(BillDetailRoute(billId))
                                }
                            )
                        }

                        composable<ReportsRoute> {
                            ReportsScreen(
                                viewModel = reportsVm,
                                onOpenCustomer = { custId ->
                                    navController.navigate(CustomerDetailRoute(custId))
                                }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(viewModel = settingsVm)
                        }
                    }
                }
            }
        }
    }
}
