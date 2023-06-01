package com.example.letsdraw

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView :DrawingView? = null
    private  var mCurrentPaintButton :ImageButton? = null

    var customProgressDialog: Dialog? = null

    val openGallery :ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){
        result ->
        if (result.resultCode == RESULT_OK && result.data != null){
            val imageView :ImageView = findViewById(R.id.iv_background)

            imageView.setImageURI(result.data?.data)
        }
    }

    private var requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach {
                val permission = it.key
                val isGranted = it.value
                if (isGranted){
                    Toast.makeText(this,"Images Access Granted",Toast.LENGTH_SHORT).show()

//                    val pickIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//                    openGallery.launch(pickIntent)
                    val pickIntent = Intent(Intent.ACTION_PICK)
                    pickIntent.type = "image/*"
                    pickIntent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
                    openGallery.launch(Intent.createChooser(pickIntent, "Select Image"))

                }
                else{
                    if (permission == Manifest.permission.READ_MEDIA_IMAGES){
                        Toast.makeText(this,"Provide Image Access",Toast.LENGTH_SHORT).show()
                    }
                }
            }


        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setBrushSize(20.toFloat())

        val palletColor :LinearLayout = findViewById(R.id.pallet_color)

        mCurrentPaintButton = palletColor[1] as ImageButton
        mCurrentPaintButton!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_selected)
        )

        val ibBrush:ImageButton = findViewById(R.id.ib_brush)
        ibBrush.setOnClickListener {
            showBrushSizeDialog()
        }

        val ivGallery :ImageButton = findViewById(R.id.ib_gallery)
        ivGallery.setOnClickListener {
                requestStoragePermission()
        }
        val ibUndo :ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        val ibRedo :ImageButton = findViewById(R.id.ib_redo)
        ibRedo.setOnClickListener {
            drawingView?.onClickRedo()
        }
        val ibSave :ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener {
            if (isReadStorageAllowed()){
                lifecycleScope.launch {
                    showProgressDialog()
                    val viewContainer: FrameLayout = findViewById(R.id.drawing_view_container)
                    saveBitmapFile(getBitmapFromView(viewContainer))
                }
            }
        }

    }

    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_MEDIA_IMAGES)){
            showDialog("App ","Let's Draw needs to access Storage")
        }else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermission.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
            }
            else{
                requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    private fun isReadStorageAllowed(): Boolean {

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        return result == PackageManager.PERMISSION_GRANTED
    }


    private fun showBrushSizeDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Choose Brush Size: ")
        R.layout.dialog_brush_size
        val smallBtn : ImageButton = brushDialog.findViewById(R.id.small_brush)
        smallBtn.setOnClickListener {
            drawingView?.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }
        val medBtn : ImageButton = brushDialog.findViewById(R.id.medium_brush)
        medBtn.setOnClickListener {
            drawingView?.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn : ImageButton = brushDialog.findViewById(R.id.large_brush)
        largeBtn.setOnClickListener {
            drawingView?.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view:View){
        if (view !== mCurrentPaintButton) {
            val colorSelected = view as ImageButton
            val color = colorSelected.tag
            drawingView?.setColor(color.toString())
            colorSelected.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_selected)
            )
            mCurrentPaintButton!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )
            mCurrentPaintButton = view
        }
    }
    private  fun showDialog(title:String ,message:String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)

        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){
                    dialog,_ -> dialog.dismiss()
            }
        builder.create().show()
    }

    private fun  getBitmapFromView(view: View):Bitmap{
        val retuBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(retuBitmap)
        val background = view.background
        if (background != null){
            background.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return retuBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?):String{
        var result = ""
        withContext(Dispatchers.IO){
            if (mBitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)

                    val directory = "/storage/emulated/0/Download/"
                    val fileName = "LetsDraw_${System.currentTimeMillis() / 1000}.png"
                    val f = File(
                        directory,fileName
                    )


                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    MediaScannerConnection.scanFile(
                        this@MainActivity,
                        arrayOf(f.absolutePath),
                        arrayOf("image/png"),
                        null
                    )
                    result = f.absolutePath
                    runOnUiThread{
                        cancelProgressDialog()
                        if (!result.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully :$result",
                                Toast.LENGTH_SHORT
                            ).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
                    }
                }
        return result
    }
    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)


        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        customProgressDialog?.show()
    }

    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result: String){
        MediaScannerConnection.scanFile(
            this,
            arrayOf(result),
            null
        ){path,uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent,"share"))
        }
    }

}