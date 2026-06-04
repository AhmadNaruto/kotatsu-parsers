package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.util.urlBuilder
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("NHENTAI", "NHentai.net", type = ContentType.HENTAI)
internal class NHentaiParser(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.NHENTAI, "nhentai.net", 25) {

	private val apiSuffix = "api/v2"

	// Required overrides for base class
	override val selectGallery = ""
	override val selectGalleryLink = ""
	override val selectGalleryTitle = ""
	override val selectGalleryImg = ""
	override val idImg = "none"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_TODAY,
		SortOrder.POPULARITY_WEEK
	)

	override val filterCapabilities = MangaListFilterCapabilities(
		isMultipleTagsSupported = true,
		isTagsExclusionSupported = true,
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
		isOriginalLocaleSupported = true,
	)

	private val nhConfig = suspendLazy {
		runCatchingCancellable {
			val response = webClient.httpGet("https://$domain/$apiSuffix/config").parseJson()
			val images = response.getJSONArray("image_servers")
			val thumbs = response.getJSONArray("thumb_servers")
			val imageServers = (0 until images.length()).map { i -> images.getString(i) }
			val thumbServers = (0 until thumbs.length()).map { i -> thumbs.getString(i) }
			imageServers to thumbServers
		}.getOrElse {
			(1..4).map { "https://i$it.$domain" } to (1..4).map { "https://t$it.$domain" }
		}
	}

	private suspend fun imageServer(): String = nhConfig.get().first.random()
	private suspend fun thumbServer(): String = nhConfig.get().second.random()

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = setOf(
			MangaTag("Sole Female", "sole-female", source),
			MangaTag("Sole Male", "sole-male", source),
			MangaTag("Stockings", "stockings", source),
			MangaTag("Group", "group", source),
			MangaTag("Schoolgirl Uniform", "schoolgirl-uniform", source),
			MangaTag("Anal", "anal", source),
			MangaTag("Nakadashi", "nakadashi", source),
			MangaTag("Blowjob", "blowjob", source),
			MangaTag("Defloration", "defloration", source),
			MangaTag("Yaoi", "yaoi", source),
			MangaTag("Yuri", "yuri", source),
			MangaTag("Glasses", "glasses", source),
			MangaTag("Bondage", "bondage", source),
			MangaTag("Big Breasts", "big-breasts", source),
		),
		availableLocales = setOf(
			Locale.ENGLISH,
			Locale.JAPANESE,
			Locale.CHINESE,
		)
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = urlBuilder().addPathSegments(apiSuffix)
		val isDefaultHome = order == SortOrder.UPDATED
			&& filter.query.isNullOrEmpty()
			&& filter.tags.isEmpty()
			&& filter.locale == null

		if (isDefaultHome) {
			url.addPathSegment("galleries")
			url.addQueryParameter("page", page.toString())
		} else {
			val query = buildString {
				filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let {
					append(it)
					append(" ")
				}

				buildQuery(filter.tags, filter.locale)
					.takeIf { it.isNotEmpty() }
					?.let { append(it) }

				if (isEmpty()) append("pages:>0")
			}

			val sort = when (order) {
				SortOrder.POPULARITY -> "popular"
				SortOrder.POPULARITY_TODAY -> "popular-today"
				SortOrder.POPULARITY_WEEK -> "popular-week"
				else -> "date"
			}

			url.addPathSegment("search")
			url.addQueryParameter("query", query)
			url.addQueryParameter("sort", sort)
			url.addQueryParameter("page", page.toString())
		}

		val json = webClient.httpGet(url.build()).parseJson()
		val server = thumbServer()
		return json.optJSONArray("result").mapJSON {
			val id = it.getInt("id")
			val title = it.extractTitle()
			Manga(
				id = generateUid("/g/$id/"),
				title = title.cleanupTitle().ifEmpty { title },
				altTitles = emptySet(),
				url = "/g/$id/",
				publicUrl = "https://$domain/g/$id/",
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = "$server/${it.getThumbnailPath()}",
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val id = manga.url.removeSurrounding("/g/", "/")
		val obj = webClient.httpGet("https://$domain/$apiSuffix/galleries/$id").parseJson()
		val tags = obj.getJSONArray("tags")
		val title = obj.extractTitle()
		val cleanedTitle = title.cleanupTitle().ifEmpty { title }

		return manga.copy(
			title = cleanedTitle,
			tags = tags.mapJSON {
				MangaTag(it.getString("name"), it.getString("slug"), source)
			}.toSet(),
			authors = tags.mapJSONNotNull { it.takeIf {
				it.getString("type") == "artist" }?.getString("name")
			}.toSet(),
			description = "Pages: ${obj.optInt("num_pages")}",
			coverUrl = "${thumbServer()}/${obj.getCoverPath()}",
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					title = cleanedTitle,
					number = 1f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = obj.optLong("upload_date") * 1000,
					branch = null,
					source = source
				)
			)
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val id = chapter.url.removeSurrounding("/g/", "/")
		val response = webClient.httpGet("https://$domain/$apiSuffix/galleries/$id").parseJson()
		val server = imageServer()
		val thumbServer = thumbServer()
		return response.getJSONArray("pages").mapJSON { page ->
			val path = page.getString("path")
			MangaPage(
				id = generateUid(path),
				url = "$server/$path",
				preview = page.optString("thumbnail").takeIf { it.isNotBlank() }
					?.let { "$thumbServer/$it" },
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	private fun JSONObject.extractTitle(): String {
		val titleObj = optJSONObject("title")
		return listOfNotNull(
			titleObj?.optString("english"),
			titleObj?.optString("pretty"),
			optString("english_title"),
			optString("japanese_title")
		).firstOrNull { it.isNotBlank() } ?: "Gallery ${optInt("id")}"
	}

	private fun JSONObject.getThumbnailPath(): String = optJSONObject("thumbnail")?.optString("path")
		?: optString("thumbnail").takeIf { it.isNotBlank() }
		?: "galleries/${optString("media_id")}/thumb.webp"

	private fun JSONObject.getCoverPath(): String = optJSONObject("cover")?.optString("path") ?: getThumbnailPath()

	private fun buildQuery(tags: Collection<MangaTag>, language: Locale?): String = buildString {
		tags.forEach { append("tag:\"${it.key}\" ") }
		language?.let { append("language:\"${it.toLanguagePath()}\" ") }
	}.trim()
}
