package org.koitharu.kotatsu.parsers.site.madara.id

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale
import java.util.TimeZone

@MangaSourceParser("ROSEVEIL", "Roseveil", "id")
internal class Roseveil(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.ROSEVEIL, 20) {

	override val configKeyDomain = ConfigKey.Domain("roseveil.org")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities = MangaListFilterCapabilities(
		isMultipleTagsSupported = true,
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
	)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = setOf(
			MangaTag("Action", "action", source),
			MangaTag("Adult", "adult", source),
			MangaTag("Adventure", "adventure", source),
			MangaTag("Animals", "animals", source),
			MangaTag("Boys Love", "boys-love", source),
			MangaTag("Comedy", "comedy", source),
			MangaTag("Crime", "crime", source),
			MangaTag("Demon", "demon", source),
			MangaTag("Drama", "drama", source),
			MangaTag("Ecchi", "ecchi", source),
			MangaTag("Fantasy", "fantasy", source),
			MangaTag("Game", "game", source),
			MangaTag("Gender Bender", "gender-bender", source),
			MangaTag("Harem", "harem", source),
			MangaTag("Historical", "historical", source),
			MangaTag("Horror", "horror", source),
			MangaTag("Isekai", "isekai", source),
			MangaTag("Josei", "josei", source),
			MangaTag("Magic", "magic", source),
			MangaTag("Manhwa", "manhwa", source),
			MangaTag("Martial Arts", "martial-arts", source),
			MangaTag("Mature", "mature", source),
			MangaTag("Medical", "medical", source),
			MangaTag("Mirror", "mirror", source),
			MangaTag("Mystery", "mystery", source),
			MangaTag("Office Workers", "office-workers", source),
			MangaTag("Project", "project", source),
			MangaTag("Psychological", "psychological", source),
			MangaTag("Regression", "regression", source),
			MangaTag("Reincarnation", "reincarnation", source),
			MangaTag("Revenge", "revenge", source),
			MangaTag("Reverse Harem", "reverse-harem", source),
			MangaTag("Romance", "romance", source),
			MangaTag("Royalty", "royalty", source),
			MangaTag("School Life", "school-life", source),
			MangaTag("Sci Fi", "sci-fi", source),
			MangaTag("Seinen", "seinen", source),
			MangaTag("Shoujo", "shoujo", source),
			MangaTag("Shounen", "shounen", source),
			MangaTag("Shounen Ai", "shounen-ai", source),
			MangaTag("Slice Of Life", "slice-of-life", source),
			MangaTag("Smut", "smut", source),
			MangaTag("Super Power", "super-power", source),
			MangaTag("Supernatural", "supernatural", source),
			MangaTag("Survival", "survival", source),
			MangaTag("Thriller", "thriller", source),
			MangaTag("Transmigration", "transmigration", source),
			MangaTag("Yaoi", "yaoi", source),
		),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.PAUSED,
		),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.COMICS,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val pageNumber = page
		val url = "https://api.$domain/api/search".toHttpUrl().newBuilder().apply {
			addQueryParameter("type", "COMIC")
			addQueryParameter("limit", "20")
			addQueryParameter("page", pageNumber.toString())

			val sort = when (order) {
				SortOrder.POPULARITY -> "views"
				SortOrder.RATING -> "rating"
				SortOrder.ALPHABETICAL -> "title"
				else -> "new"
			}
			val sortOrder = when (order) {
				SortOrder.ALPHABETICAL -> "asc"
				else -> "desc"
			}
			addQueryParameter("sort", sort)
			addQueryParameter("order", sortOrder)

			filter.query?.takeIf { it.isNotBlank() }?.let {
				addQueryParameter("q", it)
			}
			filter.states.firstOrNull()?.let { state ->
				val statusVal = when (state) {
					MangaState.ONGOING -> "ONGOING"
					MangaState.FINISHED -> "COMPLETED"
					MangaState.PAUSED -> "HIATUS"
					else -> null
				}
				statusVal?.let { addQueryParameter("status", it) }
			}
			filter.types.firstOrNull()?.let { type ->
				val typeVal = when (type) {
					ContentType.MANGA -> "MANGA"
					ContentType.MANHWA -> "MANHWA"
					ContentType.MANHUA -> "MANHUA"
					ContentType.COMICS -> "COMIC"
					else -> null
				}
				typeVal?.let { addQueryParameter("subtype", it) }
			}
			filter.tags.firstOrNull()?.let { tag ->
				addQueryParameter("genre", tag.key)
			}
		}.build()

		val json = webClient.httpGet(url).parseJson()
		val data = json.optJSONArray("data") ?: return emptyList()
		val list = ArrayList<Manga>(data.length())
		for (i in 0 until data.length()) {
			val item = data.getJSONObject(i)
			if (item.isNull("anime_latest_episode_number")) {
				continue
			}
			val title = item.getString("title")
			val slug = item.getString("slug")
			val coverUrl = item.optString("poster_image_url").takeIf { it.isNotBlank() }
			list.add(
				Manga(
					id = generateUid(slug),
					url = slug,
					publicUrl = "https://$domain/comic/$slug",
					coverUrl = coverUrl,
					title = title,
					altTitles = emptySet(),
					rating = RATING_UNKNOWN,
					tags = emptySet(),
					authors = emptySet(),
					state = null,
					source = source,
					contentRating = if (isNsfwSource) ContentRating.ADULT else ContentRating.SAFE
				)
			)
		}
		return list
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val slug = manga.url.removeSurrounding("/").substringAfterLast("/")
		val response = webClient.httpGet("https://api.$domain/api/series/comic/$slug").parseJson()
		val title = response.getString("title")
		val synopsis = response.optString("synopsis").takeIf { it.isNotBlank() }
		val thumbnail = response.optString("poster_image_url").takeIf { it.isNotBlank() }
		val author = response.optString("author_name").takeIf { it.isNotBlank() }
		val artist = response.optString("artist_name").takeIf { it.isNotBlank() }
		val status = response.optString("comic_status")

		val state = when (status?.uppercase()) {
			"ONGOING" -> MangaState.ONGOING
			"COMPLETED" -> MangaState.FINISHED
			"HIATUS" -> MangaState.PAUSED
			"CANCELED" -> MangaState.ABANDONED
			else -> null
		}

		val genres = response.optJSONArray("genres")
		val tags = genres?.mapJSON { item ->
			val name = item.getString("name")
			MangaTag(name, name.lowercase().replace(" ", "-"), source)
		}?.toSet() ?: emptySet()

		val units = response.optJSONArray("units")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
			timeZone = TimeZone.getTimeZone("UTC")
		}
		val chapters = units?.mapChapters(reversed = true) { i, item ->
			val unitSlug = item.getString("slug")
			val unitTitle = item.optString("title").takeIf { it.isNotBlank() } ?: "Chapter ${item.getString("number")}"
			val dateStr = item.optString("created_at")
			val time = if (!dateStr.isNullOrBlank()) {
				runCatching { dateFormat.parse(dateStr)?.time }.getOrNull() ?: 0L
			} else {
				0L
			}
			val chapterNumber = item.optString("number").toFloatOrNull() ?: (i + 1f)

			MangaChapter(
				id = generateUid("$slug/chapter/$unitSlug"),
				title = unitTitle,
				number = chapterNumber,
				volume = 0,
				url = "$slug/chapter/$unitSlug",
				uploadDate = time,
				source = source,
				scanlator = null,
				branch = null
			)
		} ?: emptyList()

		return manga.copy(
			title = title,
			description = synopsis,
			coverUrl = thumbnail ?: manga.coverUrl,
			authors = listOfNotNull(author, artist).filter { it.isNotBlank() }.toSet(),
			state = state,
			tags = tags,
			chapters = chapters
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val cleanUrl = if (chapter.url.contains("://")) {
			chapter.url.substringAfter("://").substringAfter("/")
		} else {
			chapter.url.removePrefix("/")
		}
		val segments = cleanUrl.split("/").filter { it.isNotEmpty() }
		val chapterSlug = segments.last()
		val seriesSlug = if (segments.size >= 3) {
			val prev = segments[segments.size - 2]
			if (prev == "chapter") {
				segments[segments.size - 3]
			} else {
				segments[segments.size - 2]
			}
		} else if (segments.size == 2) {
			segments[0]
		} else {
			segments.firstOrNull() ?: ""
		}
		val response = webClient.httpGet("https://api.$domain/api/series/comic/$seriesSlug/chapter/$chapterSlug").parseJson()
		val chapterObj = response.optJSONObject("chapter") ?: return emptyList()
		val pages = chapterObj.optJSONArray("pages") ?: return emptyList()
		return pages.mapJSON { item ->
			val url = item.getString("image_url")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source
			)
		}
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val segments = link.pathSegments
		if (segments.size >= 2 && (segments[0] == "comic" || segments[0] == "manga")) {
			val slug = segments[1]
			if (slug.isNotBlank()) {
				return resolver.resolveManga(
					parser = this,
					url = slug,
					id = generateUid(slug)
				)
			}
		}
		return null
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val host = request.url.host
		if (host == domain || host.endsWith(".$domain")) {
			val newRequest = request.newBuilder()
				.header("Referer", "https://$domain/")
				.build()
			return chain.proceed(newRequest)
		}
		return chain.proceed(request)
	}
}
