package com.mapbox.vision.teaser.view

import android.content.SharedPreferences
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.mapbox.vision.teaser.R
import com.mapbox.vision.teaser.api.ApiService
import com.mapbox.vision.teaser.api.ApiService.UrgentContact
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UrgentContactAdapter(
    private var urgentContacts: MutableList<UrgentContact>,
    private val apiService: ApiService,
    private val sharedPreferences: SharedPreferences
) :
    RecyclerView.Adapter<UrgentContactAdapter.ViewHolder>() {

    var selectedItemPosition = -1

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.contact_name)
        val rootLayout: LinearLayout = itemView.findViewById(R.id.root_layout)
        private val infoImageView: ImageView = itemView.findViewById(R.id.urgent_contact_info)
        private val deleteButton: ImageView =
            itemView.findViewById(R.id.delete_urgent_contact_button)

        init {
            itemView.setOnClickListener {
                val previousSelectedPosition = selectedItemPosition
                selectedItemPosition = if (selectedItemPosition == adapterPosition) {
                    -1
                } else {
                    adapterPosition
                }

                notifyItemChanged(previousSelectedPosition)
                notifyItemChanged(selectedItemPosition)
            }

            deleteButton.setOnClickListener {
                val context = it.context
                if (adapterPosition != -1) {
                    val selectedContact = getSelectedItem()
                    if (selectedContact != null) {
                        // Continue with deletion
                        AlertDialog.Builder(context)
                            .setTitle("Delete Urgent Contact")
                            .setMessage("Are you sure you want to delete this urgent contact?")
                            .setPositiveButton("Yes") { _, _ ->
                                deleteUrgentContact(selectedContact.id) { isSuccessful ->
                                    if (isSuccessful) {
                                        // Remove the deleted UrgentContact from the view and update the adapter
                                        removeAt(adapterPosition)

                                        // Update the sharedPreferences with the new list of urgent contacts
                                        val updatedUrgentContacts = getUrgentContacts()
                                        saveUrgentContacts(updatedUrgentContacts)
                                    } else {
                                        // Show an error if the delete request failed
                                        Toast.makeText(
                                            context,
                                            "Failed to delete the UrgentContact. Please try again.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            .setNegativeButton("No", null)
                            .show()
                    } else {
                        Toast.makeText(context, "No contact is selected", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Please select an urgent contact to delete",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            infoImageView.setOnClickListener {
                val context = it.context
                val selectedContact = urgentContacts[adapterPosition]
                AlertDialog.Builder(context)
                    .setTitle("Contact Info")
                    .setMessage("Name: ${selectedContact.name}\nEmail: ${selectedContact.email}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.urgent_contact_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = urgentContacts[position]

        // Add the position at the start of the contact name
        holder.nameTextView.text = holder.itemView.context.getString(
            R.string.contact_name_format,
            position + 1,
            contact.name
        )

        // Set different background colors for even and odd items
        if (position % 2 == 0) {
            holder.rootLayout.setBackgroundColor(Color.parseColor("#212832"))
        } else {
            holder.rootLayout.setBackgroundColor(Color.parseColor("#232E3D"))
        }

        if (selectedItemPosition == position) {
            holder.itemView.setBackgroundResource(R.color.selected_item_background)
        } else {
            // Comment out the line below if you want to keep the even-odd coloring when an item is not selected
            // holder.itemView.setBackgroundResource(R.color.default_item_background)
        }
    }

    override fun getItemCount(): Int {
        return urgentContacts.size
    }

    fun getSelectedItem(): UrgentContact? {
        return if (selectedItemPosition != -1) {
            urgentContacts[selectedItemPosition]
        } else {
            null
        }
    }

    fun getUrgentContacts(): List<UrgentContact> {
        return urgentContacts.toList()
    }

    fun updateItemAtPosition(position: Int, updatedUrgentContact: UrgentContact) {
        urgentContacts[position] = updatedUrgentContact
        notifyItemChanged(position)
    }

    fun removeAt(position: Int) {
        urgentContacts.removeAt(position)
        notifyItemRemoved(position)
    }

    private fun deleteUrgentContact(contactId: Int, onDeleteComplete: (Boolean) -> Unit) {
        apiService.deleteUrgentContact(contactId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                onDeleteComplete(response.isSuccessful)
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                onDeleteComplete(false)
            }
        })
    }

    private fun saveUrgentContacts(urgentContacts: List<UrgentContact>) {
        sharedPreferences.edit().putString("urgentContacts", Gson().toJson(urgentContacts)).apply()
    }
}
