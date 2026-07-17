package com.pokernight.player.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.screens.LoginScreen
import com.pokernight.player.ui.screens.RegisterScreen
import com.pokernight.player.ui.screens.SplashScreen
import com.pokernight.player.ui.screens.TableGameScreen
import com.pokernight.player.ui.screens.TableListScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val TABLE_LIST = "table_list"
    const val TABLE_GAME = "table_game/{tableCode}"

    fun tableGame(code: String) = "table_game/$code"
}

@Composable
fun PokerNavGraph(
    viewModel: GameViewModel = viewModel(),
) {
    val navController = rememberNavController()

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
                    navController.navigate(Routes.tableGame(code))
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.TABLE_LIST) { inclusive = true }
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
