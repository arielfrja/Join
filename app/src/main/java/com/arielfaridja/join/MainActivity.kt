package com.arielfaridja.join

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color.GREEN
import android.graphics.Color.RED
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.arielfaridja.join.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {


    internal lateinit var binding: ActivityMainBinding
    internal var redWords = arrayListOf("pay attention", "stop", "be careful")
    internal var greenWords = arrayListOf("art show", "museum", "artshow")
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission: ", "Granted")
            } else {
                Log.i("Permission: ", "Denied")
            }
        }
    private val TAG = "____Main Activity____"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        getPermissions()
        WordsLists.dangerWords = LocalFilesIO.readWordsFromFile(filesDir, RED)
        WordsLists.casualWords = LocalFilesIO.readWordsFromFile(filesDir, GREEN)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavView.setupWithNavController(navController)
        if (savedInstanceState == null) {
            navController.navigate(R.id.recognitionFragment)

        }
    }




    private fun getPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                {}
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.RECORD_AUDIO
            ) -> {//TODO: add shouldShowRequestPermissionRationale implementation
            }

            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }
    }

}