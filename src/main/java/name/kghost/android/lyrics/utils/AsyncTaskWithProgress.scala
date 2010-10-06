package name.kghost.android.lyrics.utils

import android.content.{ Context, DialogInterface }
import android.app.ProgressDialog
import android.os.AsyncTask

abstract class AsyncTaskWithProgress[A, Result](context: Context, text: String) extends AsyncTask[AnyRef, A, Result] { task =>
  private val listener = new DialogInterface.OnCancelListener {
    override def onCancel(dialog: DialogInterface): Unit = task.cancel(true)
  }
  private val dialog = With(new ProgressDialog(context)) {
    _.setOnCancelListener(listener)
  }

  override protected[utils] def onPreExecute(): Unit = {
    dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
    dialog.setMessage(text)
    dialog.show
  }

  override protected[utils] def onCancelled(): Unit = {
    dialog.dismiss
  }

  override protected[utils] def onPostExecute(result: Result): Unit = {
    dialog.dismiss
  }
}