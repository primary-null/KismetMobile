package com.kismet.mobile.ui

import android.os.Bundle
import androidx.navigation.NavController

fun NavController.navigateIfPresent(actionId: Int, args: Bundle? = null) {
    if (currentDestination?.getAction(actionId) != null) {
        navigate(actionId, args)
    }
}
