package com.turku.common

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.turku.payload.*
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import java.io.FileInputStream
import java.util.*

class SpreadSheetUtil(private val spreadSheetId: String) {
    companion object {
        private const val APPLICATION_NAME = "Turku AMK"
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
        private val CREDENTIALS_FILE_PATH = Utils.getEnv("ktor.gcp.credentialsFilePath")
        private val serviceAccount = Utils.getEnv("ktor.gcp.serviceAccount")

        private var skillLevelsSheet: List<List<String?>>? = null
        private var contentTypesSheet: List<List<String?>>? = null
        private var serviceTypesSheet: List<List<String?>>? = null
    }

    private lateinit var columnName: List<String>
    private lateinit var columnType: List<String>
    private lateinit var columnLength: List<String>
    private lateinit var columnRequirement: List<String>

    fun get(sheet: String, validateSheet: Boolean = true): List<List<String?>> {

        val filePath = FileInputStream(CREDENTIALS_FILE_PATH)

        val googleCredential = GoogleCredential.fromStream(filePath)
            .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY)).createDelegated(serviceAccount)


        val service = Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, googleCredential)
            .setApplicationName(APPLICATION_NAME).build()

        val response = service.spreadsheets().values().get(spreadSheetId, sheet).execute()

        @Suppress("UNCHECKED_CAST") val twoDeeArray = response.getValue("values") as List<List<String>>

        if (!validateSheet) {
            return twoDeeArray.toList().slice(1..twoDeeArray.toList().indices.last)
        }

        columnName = twoDeeArray.toList()[0].subList(0, twoDeeArray.toList()[0].indices.last)
        columnType = twoDeeArray.toList()[1].subList(0, twoDeeArray.toList()[1].indices.last)
        columnLength = twoDeeArray.toList()[2].subList(0, twoDeeArray.toList()[2].indices.last)
        columnRequirement = twoDeeArray.toList()[3].subList(0, twoDeeArray.toList()[3].indices.last)

        return this.parser(twoDeeArray.toList().slice(5..twoDeeArray.toList().indices.last))
    }

    private fun parser(data: List<List<String>>): List<List<String>> {
        return data.map {
            return@map it.subList(0, it.indices.last).mapIndexed { index: Int, it2: String ->
                // Check if required and exists
                dataExistenceChecker(it2, index)
                    ?: throw Error("dataExistenceChecker: Exception on '${columnName[index]}' with data $it2 is required")
                // Transform data type
                dataTypeChecker(it2, index)
                    ?: throw Error("typeChecked: Exception on '${columnName[index]}' and '${columnType[index]}' with data $it2")
                // Validate length
                dataLengthChecker(it2, index)
                    ?: throw Error("dataLengthChecker: Exception on '${columnName[index]}' and '${columnLength[index]}' with data $it2")
                it2
            }
        }
    }

    private fun getSkillLevel(id: String): List<String?>? {
        if (skillLevelsSheet == null) {
            skillLevelsSheet = this@SpreadSheetUtil.get(sheet = "Skill-levels", validateSheet = false)
        }

        return skillLevelsSheet?.find {
            return@find it[0] == id
        }
    }

    private fun getContentType(id: String): List<String?>? {
        if (contentTypesSheet == null) {
            contentTypesSheet = this@SpreadSheetUtil.get(sheet = "Content-types", validateSheet = false)
        }

        return contentTypesSheet?.find {
            return@find it[0] == id
        }
    }

    fun getResults(): MutableList<ResultPayload> {
        return mutableListOf<ResultPayload>().apply {
            addAll(this@SpreadSheetUtil.get("Results").map {
                val skillLevel = getSkillLevel(it[6]!!)
                val contentType = getContentType(it[12]!!)

                return@map ResultPayload(
                    resultId = it[0]?.toInt()!!,
                    problemId = it[1]?.toInt()!!,
                    categoryId = it[2]?.toInt()!!,
                    itemId = it[3]?.toInt()!!,
                    contentTypeId = if (it[12]?.length!! > 0) it[12]?.toInt()!! else null,
                    skillLevelId = if (it[6]?.length!! > 0) it[6]?.toInt()!! else null,
                    tutorialUrl = it[4]!!,
                    minCostEuro = it[5],
                    minSkillEN = skillLevel?.get(1),
                    minSkillFI = skillLevel?.get(2),
                    minTime = it[7],
                    tutorialNameEN = it[8],
                    tutorialNameFI = it[9],
                    tutorialIntroEN = it[10],
                    tutorialIntroFI = it[11],
                    contentTypeEN = contentType?.get(1),
                    contentTypeFI = contentType?.get(2),
                    tutorialImage = it[13]
                )
            }.toTypedArray())
        }
    }

    fun getCategories(): MutableList<CategoryPayload> {
        return mutableListOf<CategoryPayload>().apply {
            addAll(this@SpreadSheetUtil.get("Categories").map {
                return@map CategoryPayload(
                    categoryId = it[0]?.toInt()!!,
                    categoryEN = it[1]!!,
                    categoryFI = it[2]!!,
                    icon = it[3]!!,
                )
            }.toTypedArray())
        }
    }

    fun getItems(): MutableList<ItemPayload> {
        return mutableListOf<ItemPayload>().apply {
            addAll(this@SpreadSheetUtil.get("Items").map {
                return@map ItemPayload(
                    categoryId = it[0]?.toInt()!!,
                    itemId = it[1]?.toInt()!!,
                    itemEN = it[2]!!,
                    itemFI = it[3]!!,
                    icon = it[4],
                )
            }.toTypedArray())
        }
    }

    fun getProblems(): MutableList<ProblemPayload> {
        return mutableListOf<ProblemPayload>().apply {
            addAll(this@SpreadSheetUtil.get("Object-problem").map {
                return@map ProblemPayload(
                    categoryId = it[0]?.toInt()!!,
                    itemId = it[1]?.toInt()!!,
                    problemId = it[2]?.toInt()!!,
                    objectProblemEN = it[3]!!,
                    objectProblemFI = it[4],
                    searchTermsEN = it[5],
                    searchTermsFI = it[6],
                    icon = it[7]!!,
                )
            }.toTypedArray())
        }
    }

    private fun getProblemIds(problemIdsString: String): List<Long> {
        return problemIdsString.split(',').map {
            it.trim().toLong()
        }.distinct()
    }

    private fun getServiceType(id: String): List<String?>? {
        if (serviceTypesSheet == null) {
            serviceTypesSheet = this@SpreadSheetUtil.get(sheet = "Service-types", validateSheet = false)
        }

        return serviceTypesSheet?.find {
            return@find it[0]?.trim()?.lowercase() == id.trim().lowercase()
        }
    }

    fun getServices(): MutableList<ServicePayload> {
        val crsFactory = CRSFactory()
        val finnishCRS = crsFactory.createFromName("EPSG:3067")
        val globalCRS = crsFactory.createFromName("EPSG:4326")

        val ctFactory = CoordinateTransformFactory()
        val finnishToGlobalCRS = ctFactory.createTransform(finnishCRS, globalCRS)
        var coordinates = ProjCoordinate()

        return mutableListOf<ServicePayload>().apply {
            addAll(this@SpreadSheetUtil.get("Services")
                .filter { it[3]?.trim()?.length!! > 0 && it[8]?.trim()?.length!! > 0 && it[9]?.trim()?.length!! > 0 }
                .map {
                    finnishToGlobalCRS.transform(
                        ProjCoordinate(it[8]!!.toDouble(), it[9]!!.toDouble()), coordinates
                    )
                    return@map ServicePayload(
                        appServiceId = it[0]!!.toLong(),
                        appHriId = it[1]!!,
                        appBusinessId = it[2]!!,
                        problemIds = getProblemIds(it[3]!!),
                        appServiceTypeId = it[4]!!,
                        serviceTypeNameEn = getServiceType(it[4]!!)?.get(1)!!,
                        serviceTypeNameFi = getServiceType(it[4]!!)?.get(2)!!,
                        nameEN = it[5]!!,
                        nameFI = it[6]!!,
                        url = it[7]!!,
                        longitude = coordinates.x,
                        latitude = coordinates.y,
                        address = it[10]!!,
                        phone = it[11]!!,
                        email = it[12]!!,
                        otherDetailsEN = it[13]!!,
                        otherDetailsFI = it[14]!!
                    )
                }.toTypedArray()
            )
        }
    }

    private fun dataTypeChecker(data: String, index: Int): Any? {
        return try {
            return when (columnType[index]) {
                "Int" -> data.toInt()
                "Float" -> if (data != "") data.toFloat() else -1.0
                "String" -> data
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun dataExistenceChecker(data: String, index: Int): Any? {
        if (columnRequirement[index] == "!") {
            if (data == "") {
                return null
            }
            return data
        } else {
            return data
        }
    }

    private fun dataLengthChecker(data: String, index: Int): Any? {
        if (columnLength[index] == "") return data
        if (data.length >= columnLength[index].toInt()) {
            return null
        } else {
            return data
        }
    }
}
