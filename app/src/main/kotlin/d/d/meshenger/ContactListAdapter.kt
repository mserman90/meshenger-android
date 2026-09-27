/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.*

internal class ContactListAdapter(
    context: Context,
    resource: Int,
    private val contacts: List<Contact>
) : ArrayAdapter<Contact?>(
    context, resource, contacts
) {
    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView = convertView ?: inflater.inflate(R.layout.item_contact, null)
        val contact = contacts[position]

        itemView.findViewById<TextView>(R.id.contact_name).text = contact.name
        itemView.findViewById<ImageView>(R.id.contact_state).setOnClickListener {
            val state = when (contact.state) {
                Contact.State.CONTACT_ONLINE -> R.string.state_contact_online
                Contact.State.CONTACT_OFFLINE -> R.string.state_contact_offline
                Contact.State.NETWORK_UNREACHABLE -> R.string.state_contact_network_unreachable
                Contact.State.APP_NOT_RUNNING -> R.string.state_app_not_running
                Contact.State.AUTHENTICATION_FAILED -> R.string.state_authentication_failed
                Contact.State.COMMUNICATION_FAILED -> R.string.state_communication_failed
                Contact.State.PENDING -> R.string.state_contact_pending
            }
            if (contact.blocked) {
                val message = context.getString(state) + " / " + context.getString(R.string.contact_blocked)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, state, Toast.LENGTH_SHORT).show()
            }
        }

        val state = itemView.findViewById<ImageView>(R.id.contact_state)
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint()
        p.color = when (contact.state) {
            Contact.State.CONTACT_ONLINE -> Color.parseColor("#16A34A") // ISO 22324 Green (Safe/Reachable)
            Contact.State.CONTACT_OFFLINE -> Color.parseColor("#DC2626") // ISO 22324 Red (Danger/Offline)
            Contact.State.NETWORK_UNREACHABLE -> Color.parseColor("#64748B") // ISO 22324 Slate Grey (Unreachable)
            Contact.State.APP_NOT_RUNNING -> Color.parseColor("#94A3B8") // ISO 22324 Light Grey (App Inactive)
            Contact.State.AUTHENTICATION_FAILED -> Color.parseColor("#B91C1C") // ISO 22324 Dark Red (Auth Failed)
            Contact.State.COMMUNICATION_FAILED -> Color.parseColor("#F97316") // ISO 22324 Warning Orange (Error)
            Contact.State.PENDING -> Color.parseColor("#EAB308") // ISO 22324 Amber Yellow (Pending)
        }
        canvas.drawCircle(100f, 100f, 100f, p)
        if (contact.blocked) {
            // draw smaller dark red circle on top
            p.color = Color.parseColor("#ba0000")
            canvas.drawCircle(100f, 100f, 70f, p)
        }
        state.setImageBitmap(bitmap)

        return itemView
    }
}
