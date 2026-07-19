package com.pokernight.player.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.screens.LoginScreen
import com.pokernight.player.ui.screens.RegisterScreen
import com.pokernight.player.ui.screens.ScanningScreen
import com.pokernight.player.ui.screens.SplashScreen
import com.pokernight.player.ui.screens.TableGameScreen
import com.pokernight.player.ui.screens.TableJoinScreen
import com.pokernight.player.ui.screens.TableLobbyScreen
import com.pokernight.player.ui.screens.TableListScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val TABLE_LIST = "table_list"
    const val SCANNING = "scanning"
    const val TABLE_GAME = "table_game/{tableCode}"
    const val TABLE_JOIN = "table_join/{tableCode}"
    const val TABLE_LOBBY = "table_lobby/{tableCode}"

    fun tableGame(code: String) = "table_game/$code"
    fun tableJoin(code: String) = "table_join/$code"
    fun tableLobby(tableCode: String) = "table_lobby/$tableCode"
}

@Composable
fun PokerNavGraph(
    viewModel: GameViewModel = viewModel(),
) {
    val navController = rememberNavController()

    // App 回到前台时，若 socket 已断开则重连并同步牌桌状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reconnectSocketIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    if (route == Routes.LOGIN) {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.TABLE_LIST) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.TABLE_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onRegisterClick = { navController.navigate(Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = viewModel,
                onRegisterSuccess = {
                    navController.navigate(Routes.TABLE_LIST) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TABLE_LIST) {
            TableListScreen(
                viewModel = viewModel,
                onTableClick = { code ->
                    navController.navigate(Routes.tableJoin(code))
                },
                onScanClick = {
                    navController.navigate(Routes.SCANNING)
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.TABLE_LIST) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SCANNING) {
            ScanningScreen(
                onScanResult = { tableCode ->
                    navController.navigate(Routes.tableJoin(tableCode)) {
                        popUpTo(Routes.SCANNING) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TABLE_JOIN,
            arguments = listOf(navArgument("tableCode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tableCode = backStackEntry.arguments?.getString("tableCode") ?: ""
            TableJoinScreen(
                viewModel = viewModel,
                tableCode = tableCode,
                onJoinSuccess = { tableCode ->
                    navController.navigate(Routes.tableLobby(tableCode)) {
                        popUpTo(Routes.TABLE_JOIN) { inclusive = true }
                    }
                    viewModel.clearJoinResult()
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.TABLE_LOBBY,
            arguments = listOf(navArgument("tableCode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tableCode = backStackEntry.arguments?.getString("tableCode") ?: ""
            TableLobbyScreen(
                viewModel = viewModel,
                tableCode = tableCode,
                onTournamentStart = { code ->
                    navController.navigate(Routes.tableGame(code)) {
                        popUpTo(Routes.TABLE_LOBBY) { inclusive = true }
                    }
                },
                onLeave = {
                    navController.navigate(Routes.TABLE_LIST) {
                        popUpTo(Routes.TABLE_LOBBY) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.TABLE_GAME,
            arguments = listOf(navArgument("tableCode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tableCode = backStackEntry.arguments?.getString("tableCode") ?: ""
            TableGameScreen(
                viewModel = viewModel,
                tableCode = tableCode,
                onBack = {
                    viewModel.disconnectSocket()
                    navController.popBackStack()
                },
            )
        }
    }
}
