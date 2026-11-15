package com.environmentmonitor.app.data

import kotlinx.coroutines.flow.Flow

/** Create, read, update and delete for activity profiles. */
class ProfileRepository(private val dao: ProfileDao) {

    /** All profiles, presets first. Emits again whenever the table changes. */
    val profiles: Flow<List<Profile>> = dao.observeAll()

    suspend fun get(id: Long): Profile? = dao.getById(id)

    /** Creates a custom profile and returns its id. */
    suspend fun create(profile: Profile): Long =
        dao.insert(profile.copy(id = 0, isPreset = false))

    /** Updates a custom profile. Presets are read-only. */
    suspend fun update(profile: Profile) {
        val existing = dao.getById(profile.id)
        check(existing != null) { "Profile ${profile.id} does not exist" }
        check(!existing.isPreset) { "Presets are read-only" }
        dao.update(profile.copy(isPreset = false))
    }

    /** Copies any profile (preset or custom) into a new custom profile and returns its id. */
    suspend fun duplicate(id: Long): Long? {
        val source = dao.getById(id) ?: return null
        val name = uniqueCopyName(source.name, dao.allNames())
        return dao.insert(source.copy(id = 0, name = name, isPreset = false))
    }

    /** Deletes a custom profile. Returns false for presets and unknown ids. */
    suspend fun delete(id: Long): Boolean = dao.deleteCustom(id) > 0

    /** Inserts the built-in presets the first time the app runs. */
    suspend fun seedPresetsIfNeeded() {
        if (dao.presetCount() == 0) dao.insertAll(Presets.all)
    }

    companion object {
        const val MAX_NAME_LENGTH = 40

        /** "Sleep copy", then "Sleep copy 2", ... trimmed to the maximum name length. */
        fun uniqueCopyName(original: String, existing: List<String>): String {
            val taken = existing.map { it.lowercase() }.toSet()
            var candidate = "$original copy".take(MAX_NAME_LENGTH)
            var n = 2
            while (candidate.lowercase() in taken) {
                val suffix = " copy $n"
                candidate = original.take(MAX_NAME_LENGTH - suffix.length) + suffix
                n++
            }
            return candidate
        }
    }
}
