package com.miguellobato84.recetas

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseCatalogTest {
    @Test fun `release selection compares versions numerically and requires both assets`() {
        val selected = selectLatestRelease(
            listOf(
                ReleaseCandidate(10, asset(10, false), asset(10, true)),
                ReleaseCandidate(11, asset(11, false), null),
                ReleaseCandidate(9, asset(9, false), asset(9, true)),
                ReleaseCandidate(2, asset(2, false), asset(2, true)),
            ),
            installedVersionCode = 9,
        )
        assertEquals(10, selected?.versionCode)
    }

    @Test fun `release catalog excludes drafts prereleases malformed and overflow tags`() {
        val parsed = parseReleasePage("""
            [
              {"tag_name":"v10","draft":false,"prerelease":false,"assets":${assets(10)}},
              {"tag_name":"v99","draft":true,"prerelease":false,"assets":${assets(99)}},
              {"tag_name":"v100","draft":false,"prerelease":true,"assets":${assets(100)}},
              {"tag_name":"v0","draft":false,"prerelease":false,"assets":${assets(0)}},
              {"tag_name":"v2100000001","draft":false,"prerelease":false,"assets":${assets(2100000001)}}
            ]
        """.trimIndent())

        assertEquals(listOf(10), parsed.map { it.versionCode })
        assertEquals(10, selectLatestRelease(parsed, installedVersionCode = 9)?.versionCode)
        assertNull(selectLatestRelease(parsed, installedVersionCode = 10))
    }

    @Test fun `checksum parser accepts sha256sum shape and checks expected filename`() {
        val bytes = ByteArray(32) { it.toByte() }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        assertArrayEquals(bytes, parseSha256Asset("$hex  recetas-v10.apk\n", "recetas-v10.apk"))
        assertThrows(IllegalArgumentException::class.java) {
            parseSha256Asset("$hex  other.apk", "recetas-v10.apk")
        }
    }

    @Test fun `asset URLs must be the exact HTTPS GitHub release asset`() {
        val release = parseReleasePage("""
          [{"tag_name":"v10","draft":false,"prerelease":false,"assets":[
            {"name":"recetas-v10.apk","browser_download_url":"https://attacker.example/a.apk"},
            {"name":"recetas-v10.apk.sha256","browser_download_url":"https://github.com/miguellobato84/recipes-android/releases/download/v10/recetas-v10.apk.sha256"}
          ]}]
        """.trimIndent()).single()
        assertNull(selectLatestRelease(listOf(release), 9))
    }

    private fun asset(version: Int, checksum: Boolean): ReleaseAsset {
        val name = "recetas-v$version.apk" + if (checksum) ".sha256" else ""
        return ReleaseAsset(name, "https://github.com/miguellobato84/recipes-android/releases/download/v$version/$name")
    }

    private fun assets(version: Int): String {
        val apk = "recetas-v$version.apk"
        return """[
          {"name":"$apk","browser_download_url":"https://github.com/miguellobato84/recipes-android/releases/download/v$version/$apk"},
          {"name":"$apk.sha256","browser_download_url":"https://github.com/miguellobato84/recipes-android/releases/download/v$version/$apk.sha256"}
        ]"""
    }
}
