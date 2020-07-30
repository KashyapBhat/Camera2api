package kashyap.`in`.cameraapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kashyap.`in`.cameraapplication.camera.Camera2Fragment
import kashyap.`in`.cameraapplication.common.BACK_CAM


class CameraActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        fun createInstance(
            context: Context?
        ): Intent {
            return Intent(context, CameraActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(R.layout.activity_camera2)
    }

    override fun onResume() {
        super.onResume()

        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        startFragment()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                }
            }).check()
    }

    private fun startFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, Camera2Fragment.newInstance(BACK_CAM))
            .commit()
    }


}
