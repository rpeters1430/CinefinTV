package com.rpeters.cinefintv

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ManifestPolicyTest {

    @Test
    fun manifest_doesNotRequestWifiStatePermission() {
        val document = parseManifest()

        val permissions = document.getElementsByTagName("uses-permission")
        val requestedPermissions = buildSet {
            repeat(permissions.length) { index ->
                val node = permissions.item(index)
                val name = node.attributes?.getNamedItem("android:name")?.nodeValue
                if (name != null) {
                    add(name)
                }
            }
        }

        assertFalse(
            "ACCESS_WIFI_STATE limits Android TV availability on Ethernet-only devices.",
            "android.permission.ACCESS_WIFI_STATE" in requestedPermissions,
        )
    }

    @Test
    fun manifest_declaresDataExtractionRules() {
        val document = parseManifest()
        val application = document.getElementsByTagName("application").item(0)
        val dataExtractionRules = application.attributes
            ?.getNamedItem("android:dataExtractionRules")
            ?.nodeValue
        val fullBackupContent = application.attributes
            ?.getNamedItem("android:fullBackupContent")
            ?.nodeValue

        assertEquals("@xml/data_extraction_rules", dataExtractionRules)
        assertEquals("@xml/backup_rules", fullBackupContent)
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))
}
