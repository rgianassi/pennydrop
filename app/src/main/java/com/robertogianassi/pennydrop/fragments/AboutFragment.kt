package com.robertogianassi.pennydrop.fragments

import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.robertogianassi.pennydrop.R


class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_about, container, false)

        view.findViewById<TextView>(R.id.about_credits)?.apply {
            // Makes links clickable.
            movementMethod = LinkMovementMethod.getInstance()
        }

        view.findViewById<TextView>(R.id.about_icon_credits)?.apply {
            val spannableString = SpannableString(getString(R.string.penny_drop_icons))

            spannableString.setSpan(
                URLSpan("https://materialdesignicons.com/icon/currency-usd-circle-outline"),
                4,
                8,
                0
            )

            spannableString.setSpan(
                URLSpan("https://materialdesignicons.com/icon/dice-6"),
                13,
                26,
                0
            )

            spannableString.setSpan(
                URLSpan("https://materialdesignicons.com"),
                46,
                67,
                0
            )

            text = spannableString

            //Makes the links clickable.
            movementMethod = LinkMovementMethod.getInstance();
        }

        return view
    }
}