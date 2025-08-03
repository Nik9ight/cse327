package com.example.llmapp.geofence

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.llmapp.R

class GeofenceListAdapter(
    private val context: Context,
    private var geofences: MutableList<GeofenceData>,
    private val onAction: (GeofenceData, String) -> Unit
) : BaseAdapter() {
    
    override fun getCount(): Int = geofences.size
    
    override fun getItem(position: Int): GeofenceData = geofences[position]
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_geofence, parent, false)
        val geofence = geofences[position]
        
        val tvName = view.findViewById<TextView>(R.id.tvGeofenceName)
        val tvLocation = view.findViewById<TextView>(R.id.tvGeofenceLocation)
        val tvRadius = view.findViewById<TextView>(R.id.tvGeofenceRadius)
        val tvChatId = view.findViewById<TextView>(R.id.tvGeofenceChatId)
        val tvMessage = view.findViewById<TextView>(R.id.tvGeofenceMessage)
        val switchEnabled = view.findViewById<Switch>(R.id.switchGeofenceEnabled)
        val btnLocate = view.findViewById<Button>(R.id.btnLocateGeofence)
        val btnDelete = view.findViewById<Button>(R.id.btnDeleteGeofence)
        
        // Set data
        tvName.text = geofence.name
        tvLocation.text = String.format("%.6f, %.6f", geofence.latitude, geofence.longitude)
        tvRadius.text = "${geofence.radius.toInt()}m"
        tvChatId.text = if (geofence.chatId?.isNotEmpty() == true) {
            "Chat: ${geofence.chatId}"
        } else {
            "Chat: Default"
        }
        tvMessage.text = geofence.enterMessage
        switchEnabled.isChecked = geofence.isEnabled
        
        // Set listeners
        switchEnabled.setOnCheckedChangeListener { _, _ ->
            onAction(geofence, "toggle")
        }
        
        btnLocate.setOnClickListener {
            onAction(geofence, "locate")
        }
        
        btnDelete.setOnClickListener {
            onAction(geofence, "delete")
        }
        
        return view
    }
    
    fun updateGeofences(newGeofences: List<GeofenceData>) {
        geofences.clear()
        geofences.addAll(newGeofences)
        notifyDataSetChanged()
    }
}
