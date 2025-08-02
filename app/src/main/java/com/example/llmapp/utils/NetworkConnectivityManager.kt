package com.example.llmapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Network connectivity utility for graceful network handling
 * Implements the network condition checking mentioned in the reference
 */
class NetworkConnectivityManager(private val context: Context) {
    
    private val TAG = "NetworkConnectivity"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Check if network is currently available
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connectivity", e)
            false
        }
    }
    
    /**
     * Check if network has internet capability (not just connected to router)
     */
    fun hasInternetCapability(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                isNetworkAvailable() // Fallback for older versions
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet capability", e)
            false
        }
    }
    
    /**
     * Get network type for logging/debugging
     */
    fun getNetworkType(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return "None"
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
                
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Other"
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.typeName ?: "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network type", e)
            "Error"
        }
    }
    
    /**
     * Monitor network connectivity changes as a Flow
     * Useful for reacting to network state changes
     */
    fun networkConnectivityFlow(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                trySend(false)
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Network capabilities changed. Has internet: $hasInternet")
                trySend(hasInternet)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Send current state
        trySend(hasInternetCapability())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
    
    /**
     * Wait for network to become available
     * Useful for retry logic when network is down
     */
    suspend fun waitForNetwork(timeoutMs: Long = 30000): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (hasInternetCapability()) {
                Log.d(TAG, "Network became available")
                return true
            }
            
            kotlinx.coroutines.delay(1000) // Check every second
        }
        
        Log.w(TAG, "Network did not become available within ${timeoutMs}ms")
        return false
    }
}
