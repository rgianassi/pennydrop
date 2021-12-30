package com.robertogianassi.pennydrop.viewmodels

import android.app.Application
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.robertogianassi.pennydrop.data.*
import com.robertogianassi.pennydrop.game.GameHandler
import com.robertogianassi.pennydrop.game.TurnEnd
import com.robertogianassi.pennydrop.game.TurnResult
import com.robertogianassi.pennydrop.types.Player
import com.robertogianassi.pennydrop.types.Slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

// The first thing we need to do is get an instance of PennyDropRepository. To do
// this, we need to get instances of both PennyDropDatabase and PennyDropDao. But
// that presents a problem: getting the PennyDropDatabase instance requires access
// to a Context object. Normally, ViewModel classes should not access any type of
// Context object, as it can cause memory leaks due to life-cycle discrepancies
// (that is, an Activity or Fragment is disposed before the ViewModel, leaving an invalid
// context reference in the ViewModel). However, when we require a Context object,
// we can use the AndroidViewModel class instead of ViewModel as the parent class.
// This allows us to have a reference to an Application object, which is a Context
// subclass.
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private var clearText = false
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val repository: PennyDropRepository
    val currentGame = MediatorLiveData<GameWithPlayers>()
    val currentGameStatuses: LiveData<List<GameStatus>>
    val currentPlayer: LiveData<Player>
    val currentStandingsText: LiveData<String>
    val slots: LiveData<List<Slot>>
    val canRoll: LiveData<Boolean>
    val canPass: LiveData<Boolean>

    init {
        repository =
            PennyDropDatabase
                .getDatabase(application, viewModelScope)
                .pennyDropDao()
                .let { dao ->
                    PennyDropRepository.getInstance(dao)
                }
        currentGameStatuses = repository.getCurrentGameStatuses()
        currentGame.addSource(
            repository.getCurrentGameWithPlayers()
        ) { gameWithPlayers ->
            updateCurrentGame(gameWithPlayers, currentGameStatuses.value)
        }
        currentGame.addSource(currentGameStatuses) { gameStatuses ->
            updateCurrentGame(currentGame.value, gameStatuses)
        }
        currentPlayer =
            Transformations.map(currentGame) { gameWithPlayers ->
                gameWithPlayers?.players?.firstOrNull { it.isRolling }
            }
        currentStandingsText =
            Transformations.map(currentGame) { gameWithPlayers ->
                gameWithPlayers?.players?.let { players ->
                    generateCurrentStandings(players)
                }
            }
        slots =
            Transformations.map(currentGame) { gameWithPlayers ->
                Slot.mapFromGame(gameWithPlayers?.game)
            }
        canRoll = Transformations.map(currentPlayer) { player ->
            player?.isHuman == true && currentGame.value?.game?.canRoll == true
        }
        canPass = Transformations.map(currentPlayer) { player ->
            player?.isHuman == true && currentGame.value?.game?.canPass == true
        }
    }

    private fun updateCurrentGame(
        gameWithPlayers: GameWithPlayers?,
        gameStatuses: List<GameStatus>?,
    ) {
        currentGame.value = gameWithPlayers?.updateStatuses(gameStatuses)
    }

    suspend fun startGame(playersForNewGame: List<Player>) {
        // The likely question here is “Why not get the pennyCount value inside PennyDropDao
        // instead of sending it through all the layers?” The reason is the
        // PreferenceManager.getDefaultSharedPreferences function needs a reference to a Context
        // object.
        // In the docs, it says “[The getDefaultSharedPreferences() function] works from anywhere
        // in your application,” but that’s only true if you’re able to get a Context object
        // in said location. Even though we created the PennyDropDatabase with a Context
        // object and get PennyDropDao from there, we don’t have access to that object
        // anymore in this class.
        // While we could save a Context value inside PennyDropDao in some fashion, we
        // don’t want Android application context references being saved in random
        // places. The GameViewModel already has access to a Context object (via the application
        // value), so this is the best spot to access our preferences.
        repository.startGame(
            playersForNewGame,
            prefs?.getInt("pennyCount", Player.defaultPennyCount)
        )
    }

    fun roll() {
        val game = currentGame.value?.game
        val players = currentGame.value?.players
        val currentPlayer = currentPlayer.value
        val slots = slots.value
        if (game != null && players != null && currentPlayer != null && slots != null && game.canRoll
        ) {
            updateFromGameHandler(GameHandler.roll(players, currentPlayer, slots))
        }
    }

    fun pass() {
        val game = currentGame.value?.game
        val players = currentGame.value?.players
        val currentPlayer = currentPlayer.value
        if (game != null && players != null && currentPlayer != null && game.canPass
        ) {
            updateFromGameHandler(GameHandler.pass(players, currentPlayer))
        }
    }

    private fun updateFromGameHandler(result: TurnResult) {
        val game = currentGame.value?.let { currentGameWithPlayers ->
            currentGameWithPlayers.game.copy(
                gameState = if (result.isGameOver) GameState.Finished else GameState.Started,
                lastRoll = result.lastRoll,
                filledSlots = updateFilledSlots(result, currentGameWithPlayers.game.filledSlots),
                //Note: This generates with old values since the game's yet to be updated.
                currentTurnText = generateTurnText(result),
                canPass = result.canPass,
                canRoll = result.canRoll,
                endTime = if (result.isGameOver) OffsetDateTime.now() else null
            )
        } ?: return
        val statuses = currentGameStatuses.value?.map { status ->
            when (status.playerId) {
                result.previousPlayer?.playerId -> {
                    status.copy(
                        isRolling = false,
                        pennies = status.pennies + (result.coinChangeCount ?: 0)
                    )
                }
                result.currentPlayer?.playerId -> {
                    status.copy(
                        isRolling = !result.isGameOver,
                        pennies = status.pennies +
                                if (!result.playerChanged) {
                                    result.coinChangeCount ?: 0
                                } else 0
                    )
                }
                else -> status
            }
        } ?: emptyList()
        viewModelScope.launch {
            repository.updateGameAndStatuses(game, statuses)
            if (result.currentPlayer?.isHuman == false) {
                playAITurn()
            }
        }
    }

    private fun updateFilledSlots(
        result: TurnResult,
        filledSlots: List<Int>,
    ) = when {
        result.clearSlots -> emptyList()
        result.lastRoll != null && result.lastRoll != 6 -> filledSlots + result.lastRoll
        else -> filledSlots
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

    private fun generateTurnText(result: TurnResult): String {
        val currentText = if (clearText) "" else currentGame.value?.game?.currentTurnText ?: ""
        clearText = result.turnEnd != null
        val currentPlayerName = result.currentPlayer?.playerName ?: "???"
        return when {
            result.isGameOver -> generateGameOverText()
            result.turnEnd == TurnEnd.Bust -> "${
                ohNoPhrases.shuffled().first()
            } ${result.previousPlayer?.playerName} rolled a ${result.lastRoll}. They collected ${result.coinChangeCount} pennies for a total of ${result.previousPlayer?.pennies}.\n$currentText"
            result.turnEnd == TurnEnd.Pass -> "${result.previousPlayer?.playerName} passed. They currently have ${result.previousPlayer?.pennies} pennies.\n$currentText"
            result.lastRoll != null -> "$currentPlayerName rolled a ${result.lastRoll}.\n$currentText"
            else -> ""
        }
    }

    private fun generateGameOverText(): String {
        val statuses = currentGameStatuses.value
        val players = currentGame.value?.players?.map { player ->
            player.apply {
                pennies = statuses
                    ?.firstOrNull { it.playerId == playerId }
                    ?.pennies
                    ?: Player.defaultPennyCount
            }
        }
        val winningPlayer = players
            ?.firstOrNull { !it.penniesLeft() || it.isRolling }
            ?.apply { pennies = 0 }
        if (players == null || winningPlayer == null) return "N/A"
        val sortedPlayersString = players
            .sortedBy { it.pennies }
            .joinToString("\n") {
                "\t${it.playerName} - ${it.pennies} pennies."
            }
        return """
            |Game Over!
            |${winningPlayer.playerName} is the winner!
            |
            |Final Scores:
            |$sortedPlayersString
            """.trimMargin()
    }

    private suspend fun playAITurn() {
        delay(if (prefs.getBoolean("fastAI", false)) 1000 else 3000)
        val game = currentGame.value?.game
        val players = currentGame.value?.players
        val currentPlayer = currentPlayer.value
        val slots = slots.value
        if (game != null && players != null && currentPlayer != null && slots != null
        ) {
            GameHandler
                .playAITurn(players, currentPlayer, slots, game.canPass)
                ?.let { result ->
                    updateFromGameHandler(result)
                }
        }
    }

    private val ohNoPhrases = listOf(
        "Oh no!",
        "Bummer!",
        "Dang.",
        "Whoops.",
        "Ah, fiddlesticks.",
        "Oh, kitty cats.",
        "Piffle.",
        "Well, crud.",
        "Ah, cinnamon bits.",
        "Ooh, bad luck.",
        "Shucks!",
        "Woopsie daisy.",
        "Nooooooo!",
        "Aw, rats and bats.",
        "Blood and thunder!",
        "Gee whillikins.",
        "Well that's disappointing.",
        "I find your lack of luck disturbing.",
        "That stunk, huh?",
        "Uff da."
    )
}