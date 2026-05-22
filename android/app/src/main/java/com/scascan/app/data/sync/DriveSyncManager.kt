package com.scascan.app.data.sync

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import com.scascan.app.data.local.UserProfileStore
import com.scascan.app.data.model.ProfileExport
import com.scascan.app.data.model.SyncData
import com.scascan.app.data.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository,
    private val profileStore: UserProfileStore
) {
    private val gson = Gson()
    private val SYNC_FILE_NAME = "scascan_sync.json"

    suspend fun sync(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d("DriveSync", "Starting sync process...")
        try {
            val drive = getDriveService(accessToken)
            
            // 1. Download existing data from Drive
            Log.d("DriveSync", "Downloading data...")
            val existingData = downloadData(drive)
            Log.d("DriveSync", "Download complete, found data: ${existingData != null}")
            
            // 2. Merge local data with Drive data
            val localLogs = logRepository.getAllEntries()
            val mergedLogs = if (existingData != null) {
                // Deduplicate by timestamp and content
                (localLogs + existingData.logs).distinctBy { it.timestamp }
            } else {
                localLogs
            }
            
            // 3. Update local DB with merged logs
            logRepository.upsertEntries(mergedLogs)
            
            // 4. Update profile if Drive version is newer (optional, for now just export local)
            val currentProfile = ProfileExport(
                name = profileStore.name,
                age = profileStore.age,
                heightCm = profileStore.heightCm,
                weightKg = profileStore.weightKg,
                isMale = profileStore.isMale,
                activityIndex = profileStore.activityIndex,
                goalIndex = profileStore.goalIndex,
                aiCalorieTarget = profileStore.aiCalorieTarget,
                aiProteinTarget = profileStore.aiProteinTarget,
                aiCarbsTarget = profileStore.aiCarbsTarget,
                aiFatTarget = profileStore.aiFatTarget
            )

            // 5. Upload merged data back to Drive
            val syncData = SyncData(
                profile = currentProfile,
                logs = mergedLogs,
                lastUpdated = System.currentTimeMillis()
            )
            uploadData(drive, syncData)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DriveSync", "Sync failed", e)
            Result.failure(e)
        }
    }

    private fun getDriveService(accessToken: String): Drive {
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            { request -> request.headers.authorization = "Bearer $accessToken" }
        ).setApplicationName("ScaScan").build()
    }

    private fun downloadData(drive: Drive): SyncData? {
        val files = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$SYNC_FILE_NAME'")
            .setFields("files(id, name)")
            .execute()
        
        val fileId = files.files.firstOrNull()?.id ?: return null
        
        val tempFile = File(context.cacheDir, SYNC_FILE_NAME)
        val outputStream = FileOutputStream(tempFile)
        drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        outputStream.close()
        
        return try {
            gson.fromJson(tempFile.readText(), SyncData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun uploadData(drive: Drive, data: SyncData) {
        val tempFile = File(context.cacheDir, SYNC_FILE_NAME)
        tempFile.writeText(gson.toJson(data))

        val metadata = com.google.api.services.drive.model.File().apply {
            name = SYNC_FILE_NAME
            parents = listOf("appDataFolder")
        }
        
        val content = FileContent("application/json", tempFile)
        
        val existingFiles = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$SYNC_FILE_NAME'")
            .setFields("files(id)")
            .execute()
        
        val fileId = existingFiles.files.firstOrNull()?.id
        if (fileId != null) {
            drive.files().update(fileId, metadata, content).execute()
        } else {
            drive.files().create(metadata, content).execute()
        }
    }
}
