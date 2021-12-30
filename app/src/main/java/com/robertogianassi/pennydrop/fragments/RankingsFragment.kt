package com.robertogianassi.pennydrop.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.robertogianassi.pennydrop.R
import com.robertogianassi.pennydrop.adapters.PlayerSummaryAdapter
import com.robertogianassi.pennydrop.viewmodels.RankingsViewModel

class RankingsFragment : Fragment() {
    private val viewModel by activityViewModels<RankingsViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rankings, container, false)
        val playerSummaryAdapter = PlayerSummaryAdapter()

        if (view is RecyclerView) {
            with(view) {
                adapter = playerSummaryAdapter
                addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            }
        }

        viewModel.playerSummaries.observe(viewLifecycleOwner) { summaries ->
            playerSummaryAdapter.submitList(summaries)
        }

        return view
    }
}
