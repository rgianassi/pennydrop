package com.robertogianassi.pennydrop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.robertogianassi.pennydrop.game.AI
import com.robertogianassi.pennydrop.types.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Database(
    exportSchema = false,
    version = 1,
    entities = [
        Game::class,
        Player::class,
        GameStatus::class,
    ],
)
@TypeConverters(Converters::class)
abstract class PennyDropDatabase : RoomDatabase() {
    abstract fun pennyDropDao(): PennyDropDao

    companion object {
        @Volatile
        private var instance: PennyDropDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): PennyDropDatabase =
            this.instance ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context,
                    PennyDropDatabase::class.java,
                    "PennyDropDatabase"
                ).addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch {
                            instance?.pennyDropDao()?.insertPlayers(
                                AI.basicAI.map(AI::toPlayer)
                            )
                        }
                    }
                }).build()
                this.instance = instance
                instance
            }
    }
}
