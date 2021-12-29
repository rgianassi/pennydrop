package com.robertogianassi.pennydrop.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertogianassi.pennydrop.game.GameHandler
import com.robertogianassi.pennydrop.game.TurnEnd
import com.robertogianassi.pennydrop.game.TurnResult
import com.robertogianassi.pennydrop.types.Player
import com.robertogianassi.pennydrop.types.Slot
import com.robertogianassi.pennydrop.types.clear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private var players: List<Player> = emptyList()

    val slots =
        MutableLiveData(
            (1..6).map { slotNum -> Slot(slotNum, slotNum != 6) }
        )

    val currentPlayer = MutableLiveData<Player?>()

    val canRoll = MutableLiveData(false)
    val canPass = MutableLiveData(false)

    val currentTurnText = MutableLiveData("")
    val currentStandingsText = MutableLiveData("")

    private var clearText = false

    fun startGame(playersForNewGame: List<Player>) {
        players = playersForNewGame
        currentPlayer.value = players.firstOrNull()?.apply {
            this?.isRolling = true
        }
        canRoll.value = true
        canPass.value = false
        slots.value?.clear()
        slots.notifyChange()
        currentTurnText.value = "The game has begun!\n"
        currentStandingsText.value = generateCurrentStandings(players)
    }

    fun roll() {
        slots.value?.let { currentSlots ->
            val currentPlayer = players.firstOrNull { it.isRolling }
            // Comparing against true saves us a null check
            if (currentPlayer != null && canRoll.value == true) {
                updateFromGameHandler(
                    GameHandler.roll(players, currentPlayer, currentSlots)
                )
            }
        }
    }

    fun pass() {
        val currentPlayer = players.firstOrNull { it.isRolling }
        // Comparing against true saves us a null check
        if (currentPlayer != null && canPass.value == true) {
            updateFromGameHandler(GameHandler.pass(players, currentPlayer))
        }
    }

    private fun updateFromGameHandler(result: TurnResult) {
        if (result.currentPlayer != null) {
            currentPlayer.value?.addPennies(result.coinChangeCount ?: 0)
            currentPlayer.value = result.currentPlayer
            players.forEach { player ->
                player.isRolling = result.currentPlayer == player
            }
        }
        if (result.lastRoll != null) {
            slots.value?.let { currentSlots ->
                updateSlots(result, currentSlots, result.lastRoll)
            }
        }
        currentTurnText.value = generateTurnText(result)
        currentStandingsText.value = generateCurrentStandings(players)
        canRoll.value = result.canRoll
        canPass.value = result.canPass
        if (!result.isGameOver && result.currentPlayer?.isHuman == false) {
            canRoll.value = false
            canPass.value = false
            playAITurn()
        }
    }

    private fun playAITurn() {
        viewModelScope.launch {
            delay(1000)
            slots.value?.let { currentSlots ->
                val currentPlayer = players.firstOrNull { it.isRolling }
                if (currentPlayer != null && !currentPlayer.isHuman) {
                    GameHandler.playAITurn(
                        players,
                        currentPlayer,
                        currentSlots,
                        canPass.value == true
                    )?.let { result ->
                        updateFromGameHandler(result)
                    }
                }
            }
        }
    }

    private fun updateSlots(result: TurnResult, currentSlots: List<Slot>, lastRoll: Int) {
        if (result.clearSlots) {
            currentSlots.clear()
        }
        currentSlots.firstOrNull { it.lastRolled }?.apply { lastRolled = false }
        currentSlots.getOrNull(lastRoll - 1)?.also { slot ->
            if (!result.clearSlots && slot.canBeFilled) slot.isFilled = true
            slot.lastRolled = true
        }
        slots.notifyChange()
    }

    private fun generateTurnText(result: TurnResult): String {
        if (clearText) currentTurnText.value = ""
        clearText = result.turnEnd != null
        val currentText = currentTurnText.value ?: ""
        val currentPlayerName = result.currentPlayer?.playerName ?: "???"
        val previousPlayerName = result.previousPlayer?.playerName ?: "???"
        return when {
            result.isGameOver ->
                """
                |Game Over!
                |$currentPlayerName is the winner!
                |
                |${generateCurrentStandings(this.players, "Final Scores:\n")}
                """.trimMargin()
            result.turnEnd == TurnEnd.Bust -> "$currentText\n$previousPlayerName rolled a ${result.lastRoll} and busted with ${result.coinChangeCount} pennies."
            result.turnEnd == TurnEnd.Pass -> "$currentText\n$currentPlayerName passed with ${result.coinChangeCount} pennies."
            result.lastRoll != null -> "$currentText\n$currentPlayerName rolled a ${result.lastRoll}."
            else -> ""
        }
    }
}

private fun <T> MutableLiveData<List<T>>.notifyChange() {
    // UI is notified only if the slots variable changes
    value = value
}

private fun generateCurrentStandings(
    players: List<Player>,
    headerText: String = "Current Standings:",
) =
    players.sortedBy { it.pennies }.joinToString(
        separator = "\n",
        prefix = "$headerText\n"
    ) {
        "\t${it.playerName} - ${it.pennies} pennies"
    }
