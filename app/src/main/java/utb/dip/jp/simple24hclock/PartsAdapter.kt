package utb.dip.jp.simple24hclock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PartsAdapter(
    private val names: MutableList<String>,
    private val keys: MutableList<String>,
    private val onPartSelected: (String) -> Unit
) : RecyclerView.Adapter<PartsAdapter.ViewHolder>() {

    private var selectedPosition = 0 // 初期選択

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tv_part_name)

        init {
            view.setOnClickListener {
                val oldPos = selectedPosition
                selectedPosition = bindingAdapterPosition
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                onPartSelected(keys[selectedPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_part_chip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.nameText.text = names[position]
        val selected = position == selectedPosition
        holder.itemView.isSelected = selected
        holder.nameText.isSelected = selected
    }

    override fun getItemCount() = names.size

    fun resetSelection() {
        val oldPos = selectedPosition
        selectedPosition = -1
        notifyItemChanged(oldPos)
    }
}
