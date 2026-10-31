package dev.justinsmith.interval

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The launcher shortcuts' target, invisible. The system starts a static shortcut's activity clearing its task,
 * which would end a running workout; so the shortcut starts this, in a task of its own, and this hands the
 * shortcut to [MainActivity] in the app's task, bringing it forward as it is ([MainActivity.onNewIntent]).
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shortcut = intent.getStringExtra(EXTRA_SHORTCUT)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_SHORTCUT, shortcut)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        finish()
    }

    companion object {
        /** The shortcut's [dev.justinsmith.interval.nav.Shortcut.id], as `res/xml/shortcuts.xml` names it. */
        const val EXTRA_SHORTCUT = "dev.justinsmith.interval.SHORTCUT"
    }
}
