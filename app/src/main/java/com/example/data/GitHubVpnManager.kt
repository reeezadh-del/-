package com.example.data

import android.util.Base64
import com.example.model.ProxyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GitHubVpnManager {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    const val WORKFLOW_FILE_PATH = ".github/workflows/vpn-server-builder.yml"
    private const val LIVE_BRANCH = "vpn-live"
    private const val LIVE_JSON_PATH = "github-vpn-live.json"
    private const val LIVE_SUB_PATH = "github-vpn-sub.txt"

    enum class GitHubServerProtocol(val id: String, val labelFa: String) {
        VLESS_WS("vless-ws", "VLESS + WS + TLS"),
        VMESS_WS("vmess-ws", "VMess + WS + TLS"),
        TROJAN_WS("trojan-ws", "Trojan + WS + TLS")
    }

    data class GitHubLiveServerResult(
        val runId: Long?,
        val repoFullName: String,
        val protocol: String,
        val tunnelHost: String,
        val profiles: List<ProxyProfile>,
        val rawSubscriptionUrl: String,
        val workflowHtmlUrl: String
    )

    data class GitHubGistResult(
        val gistId: String,
        val rawUrl: String,
        val htmlUrl: String,
        val serverCount: Int
    )

    data class GitHubRepoDiscoveryItem(
        val repoFullName: String,
        val stars: Int,
        val description: String,
        val profiles: List<ProxyProfile>
    )

    private fun buildClient(useLocalProxy: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(16, TimeUnit.SECONDS)
        if (useLocalProxy) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10808)))
        }
        return builder.build()
    }

    private fun requestBuilder(url: String, token: String? = null): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SeparNet-Android/1.0")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${token.trim()}")
        }
        return builder
    }

    suspend fun deployAndStartGitHubServer(
        token: String,
        repoInput: String,
        protocol: GitHubServerProtocol,
        cleanCfIp: String,
        useLocalProxy: Boolean = false,
        onProgress: (String) -> Unit
    ): Result<GitHubLiveServerResult> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("توکن شخصی گیت‌هاب (PAT) را وارد کنید")
            )
        }

        try {
            val client = buildClient(useLocalProxy)
            onProgress("تأیید اعتبار توکن گیت‌هاب…")

            val username = fetchAuthenticatedUsername(client, cleanToken)
            val resolvedRepo = resolveRepoFullName(username, repoInput)
            val owner = resolvedRepo.substringBefore("/")
            val repoName = resolvedRepo.substringAfter("/")

            onProgress("بررسی مخزن $resolvedRepo…")
            val defaultBranch = ensureRepositoryExists(client, cleanToken, owner, repoName)

            onProgress("نصب یا بروزرسانی ورک‌فلو سرور sing-box در گیت‌هاب…")
            ensureWorkflowFileCommitted(client, cleanToken, owner, repoName, defaultBranch)

            // Wait briefly for GitHub Actions to index new/updated workflow file
            delay(1800)

            val dispatchStartedAtMs = System.currentTimeMillis()
            onProgress("شروع اجرای سرور روی GitHub Actions…")
            dispatchWorkflow(
                client = client,
                token = cleanToken,
                owner = owner,
                repoName = repoName,
                branch = defaultBranch,
                protocol = protocol.id,
                cleanCfIp = cleanCfIp.trim().ifBlank { "162.159.192.1" }
            )

            // Poll runs & live json up to ~110 seconds
            var targetRunId: Long? = null
            var htmlUrl = "https://github.com/$owner/$repoName/actions"
            for (attempt in 1..36) {
                delay(3000)
                val runInfo = findLatestDispatchRun(client, cleanToken, owner, repoName, dispatchStartedAtMs - 15_000L)
                if (runInfo != null) {
                    targetRunId = runInfo.first
                    val status = runInfo.second
                    val conclusion = runInfo.third.first
                    htmlUrl = runInfo.third.second
                    if (status == "completed" && conclusion != "success") {
                        return@withContext Result.failure(
                            IllegalStateException("اجرای GitHub Actions ناموفق بود ($conclusion). دسترسی Actions/Permissions مخزن را بررسی کنید.")
                        )
                    }
                    onProgress("سرور در حال راه‌اندازی (Run #$targetRunId • $status)…")
                } else {
                    onProgress("در انتظار صف اجرای GitHub Actions (${attempt * 3}s)…")
                }

                val liveResult = readLiveBranchNode(
                    client = client,
                    token = cleanToken,
                    owner = owner,
                    repoName = repoName,
                    expectedRunId = targetRunId,
                    minTimestampMs = dispatchStartedAtMs - 10_000L,
                    workflowHtmlUrl = htmlUrl
                )
                if (liveResult != null) {
                    return@withContext Result.success(liveResult)
                }
            }

            Result.failure(
                IllegalStateException("ساخت سرور بیش از حد طول کشید. کمی بعد «خواندن سرور فعال» را بزنید تا از آخرین وضعیت مخزن خوانده شود.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchExistingLiveServer(
        token: String,
        repoInput: String,
        useLocalProxy: Boolean = false
    ): Result<GitHubLiveServerResult> = withContext(Dispatchers.IO) {
        try {
            val client = buildClient(useLocalProxy)
            val cleanToken = token.trim()
            val resolvedRepo = if (repoInput.contains("/")) {
                repoInput.trim()
            } else {
                if (cleanToken.isBlank()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("نام مخزن را به صورت owner/repo وارد کنید")
                    )
                }
                val username = fetchAuthenticatedUsername(client, cleanToken)
                resolveRepoFullName(username, repoInput)
            }
            val owner = resolvedRepo.substringBefore("/")
            val repoName = resolvedRepo.substringAfter("/")

            val result = readLiveBranchNode(
                client = client,
                token = cleanToken.ifBlank { null },
                owner = owner,
                repoName = repoName,
                expectedRunId = null,
                minTimestampMs = 0L,
                workflowHtmlUrl = "https://github.com/$owner/$repoName/actions"
            ) ?: return@withContext Result.failure(
                IllegalStateException("هنوز سرور فعالی روی شاخه vpn-live این مخزن یافت نشد.")
            )
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopGitHubServerRun(
        token: String,
        repoFullName: String,
        runId: Long,
        useLocalProxy: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = buildClient(useLocalProxy)
            val req = requestBuilder(
                "https://api.github.com/repos/$repoFullName/actions/runs/$runId/cancel",
                token
            ).post("".toRequestBody(JSON_MEDIA)).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 202 && resp.code != 409) {
                    return@withContext Result.failure(
                        IllegalStateException("خطا در توقف سرور گیت‌هاب (HTTP ${resp.code})")
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateGistSubscription(
        token: String,
        existingGistId: String,
        profiles: List<ProxyProfile>,
        includeFreshWarp: Boolean,
        useLocalProxy: Boolean = false
    ): Result<GitHubGistResult> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("برای ساخت اشتراک شخصی در GitHub Gist، توکن گیت‌هاب (PAT با دسترسی gist) لازم است.")
            )
        }
        try {
            val allNodes = profiles.toMutableList()
            if (includeFreshWarp) {
                val freshWarp = WarpProvisioner.registerWarpAccount(useLocalProxyIfConnected = useLocalProxy).getOrNull()
                if (freshWarp != null) {
                    allNodes.add(0, freshWarp)
                }
            }
            val uriLines = allNodes.mapNotNull { it.rawUri.takeIf { uri -> uri.isNotBlank() } }.distinct()
            if (uriLines.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("هیچ سرور معتبری برای قرار دادن در اشتراک گیت‌هاب وجود ندارد (یا ساخت WARP ناموفق بود).")
                )
            }

            val rawSubText = uriLines.joinToString("\n")
            val b64SubText = Base64.encodeToString(rawSubText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            val filesObj = JSONObject().apply {
                put("separnet-sub.txt", JSONObject().put("content", rawSubText))
                put("separnet-sub-base64.txt", JSONObject().put("content", b64SubText))
            }
            val bodyObj = JSONObject().apply {
                put("description", "SeparNet VPN Personal GitHub Subscription")
                put("public", false)
                put("files", filesObj)
            }

            val client = buildClient(useLocalProxy)
            val cleanGistId = existingGistId.trim()
            val req = if (cleanGistId.isNotBlank()) {
                requestBuilder("https://api.github.com/gists/$cleanGistId", cleanToken)
                    .patch(bodyObj.toString().toRequestBody(JSON_MEDIA))
                    .build()
            } else {
                requestBuilder("https://api.github.com/gists", cleanToken)
                    .post(bodyObj.toString().toRequestBody(JSON_MEDIA))
                    .build()
            }

            client.newCall(req).execute().use { resp ->
                val rawResp = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    // If existing gist was deleted/not found, retry as new gist
                    if (cleanGistId.isNotBlank() && resp.code == 404) {
                        return@withContext createOrUpdateGistSubscription(
                            token = cleanToken,
                            existingGistId = "",
                            profiles = allNodes,
                            includeFreshWarp = false,
                            useLocalProxy = useLocalProxy
                        )
                    }
                    return@withContext Result.failure(
                        IllegalStateException("خطای گیت‌هاب Gist (HTTP ${resp.code}): ${extractGitHubError(rawResp)}")
                    )
                }
                val json = JSONObject(rawResp)
                val gistId = json.optString("id")
                val htmlUrl = json.optString("html_url")
                val rawUrl = json.optJSONObject("files")
                    ?.optJSONObject("separnet-sub.txt")
                    ?.optString("raw_url")
                    .orEmpty()
                Result.success(
                    GitHubGistResult(
                        gistId = gistId,
                        rawUrl = rawUrl,
                        htmlUrl = htmlUrl,
                        serverCount = uriLines.size
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchAndCollectGitHubServers(
        query: String = "v2ray config vless",
        useLocalProxy: Boolean = false
    ): Result<List<GitHubRepoDiscoveryItem>> = withContext(Dispatchers.IO) {
        try {
            val client = buildClient(useLocalProxy)
            val encodedQ = URLEncoder.encode(query.ifBlank { "v2ray config vless" }, "UTF-8")
            val searchReq = requestBuilder(
                "https://api.github.com/search/repositories?q=$encodedQ&sort=updated&order=desc&per_page=8"
            ).get().build()

            val reposJson = client.newCall(searchReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("خطای جستجوی گیت‌هاب (HTTP ${resp.code}): ${extractGitHubError(raw)}")
                    )
                }
                JSONObject(raw).optJSONArray("items") ?: JSONArray()
            }

            val repoCandidates = (0 until reposJson.length()).mapNotNull { idx ->
                val item = reposJson.optJSONObject(idx) ?: return@mapNotNull null
                val fullName = item.optString("full_name")
                val branch = item.optString("default_branch", "main")
                val stars = item.optInt("stargazers_count", 0)
                val desc = item.optString("description", "")
                if (fullName.isBlank()) null else RepoCandidate(fullName, branch, stars, desc)
            }.take(6)

            val candidateFilenames = listOf(
                "Sub1.txt", "sub.txt", "all.txt", "All_Configs_Sub.txt",
                "vless.txt", "splitted/vless", "README.md"
            )

            val discovered = coroutineScope {
                repoCandidates.map { repo ->
                    async {
                        val collected = mutableListOf<ProxyProfile>()
                        for (file in candidateFilenames) {
                            val rawUrl = "https://raw.githubusercontent.com/${repo.fullName}/${repo.defaultBranch}/$file"
                            val req = Request.Builder()
                                .url(rawUrl)
                                .header("User-Agent", "SeparNet-Android/1.0")
                                .get()
                                .build()
                            val parsed = runCatching {
                                client.newCall(req).execute().use { r ->
                                    if (!r.isSuccessful) emptyList()
                                    else ConfigUriParser.parseBulk(
                                        r.body?.string().orEmpty(),
                                        subscriptionSource = rawUrl
                                    )
                                }
                            }.getOrDefault(emptyList())
                            if (parsed.isNotEmpty()) {
                                collected.addAll(parsed.filter { it.protocol.supportsDirectTunnel }.take(8))
                                if (collected.size >= 8) break
                            }
                        }

                        if (collected.isEmpty()) {
                            null
                        } else {
                            val tested = collected.distinctBy { it.rawUri }.take(6).map { p ->
                                val ms = NetworkProbe.measureTcpLatency(p.host, p.port, timeoutMs = 1800)
                                p.copy(latencyMs = ms, lastTestedAt = System.currentTimeMillis())
                            }.filter { (it.latencyMs ?: -1L) > 0L }
                                .sortedBy { it.latencyMs }

                            if (tested.isEmpty()) null
                            else GitHubRepoDiscoveryItem(
                                repoFullName = repo.fullName,
                                stars = repo.stars,
                                description = repo.description,
                                profiles = tested
                            )
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (discovered.isEmpty()) {
                Result.failure(IllegalStateException("سرور قابل دسترسی در مخازن جستجو‌شده یافت نشد؛ عبارت دیگری امتحان کنید."))
            } else {
                Result.success(discovered)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class RepoCandidate(
        val fullName: String,
        val defaultBranch: String,
        val stars: Int,
        val description: String
    )

    private fun fetchAuthenticatedUsername(client: OkHttpClient, token: String): String {
        val req = requestBuilder("https://api.github.com/user", token).get().build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("احراز هویت گیت‌هاب ناموفق بود (HTTP ${resp.code}): ${extractGitHubError(raw)}")
            }
            val login = JSONObject(raw).optString("login")
            if (login.isBlank()) throw IllegalStateException("نام کاربری گیت‌هاب دریافت نشد")
            return login
        }
    }

    private fun resolveRepoFullName(username: String, repoInput: String): String {
        val trimmed = repoInput.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .trim('/')
        return when {
            trimmed.isBlank() -> "$username/separ-vpn-node"
            trimmed.contains("/") -> trimmed
            else -> "$username/$trimmed"
        }
    }

    private fun ensureRepositoryExists(
        client: OkHttpClient,
        token: String,
        owner: String,
        repoName: String
    ): String {
        val getReq = requestBuilder("https://api.github.com/repos/$owner/$repoName", token).get().build()
        client.newCall(getReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                return JSONObject(raw).optString("default_branch", "main").ifBlank { "main" }
            }
            if (resp.code != 404) {
                throw IllegalStateException("خطا در دسترسی به مخزن $owner/$repoName (HTTP ${resp.code}): ${extractGitHubError(raw)}")
            }
        }

        // Create private repo under authenticated user
        val createBody = JSONObject().apply {
            put("name", repoName)
            put("description", "SeparNet Cloud VPN Node powered by GitHub Actions")
            put("private", true)
            put("auto_init", true)
        }
        val postReq = requestBuilder("https://api.github.com/user/repos", token)
            .post(createBody.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(postReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("امکان ساخت مخزن $repoName وجود نداشت (HTTP ${resp.code}): ${extractGitHubError(raw)}")
            }
            return JSONObject(raw).optString("default_branch", "main").ifBlank { "main" }
        }
    }

    private fun ensureWorkflowFileCommitted(
        client: OkHttpClient,
        token: String,
        owner: String,
        repoName: String,
        branch: String
    ) {
        val url = "https://api.github.com/repos/$owner/$repoName/contents/$WORKFLOW_FILE_PATH?ref=$branch"
        var existingSha: String? = null
        var existingText = ""
        val getReq = requestBuilder(url, token).get().build()
        client.newCall(getReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                existingSha = json.optString("sha").takeIf { it.isNotBlank() }
                val b64 = json.optString("content").replace("\n", "")
                if (b64.isNotBlank()) {
                    existingText = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                }
            }
        }

        if (existingText.trim() == WORKFLOW_YAML.trim()) {
            return
        }

        val encodedContent = Base64.encodeToString(WORKFLOW_YAML.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val putBody = JSONObject().apply {
            put("message", "Add/update SeparNet VPN Server Builder workflow")
            put("content", encodedContent)
            put("branch", branch)
            if (existingSha != null) put("sha", existingSha)
        }
        val putReq = requestBuilder(
            "https://api.github.com/repos/$owner/$repoName/contents/$WORKFLOW_FILE_PATH",
            token
        ).put(putBody.toString().toRequestBody(JSON_MEDIA)).build()
        client.newCall(putReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("خطا در آپلود ورک‌فلو گیت‌هاب (HTTP ${resp.code}): ${extractGitHubError(raw)}")
            }
        }
    }

    private fun dispatchWorkflow(
        client: OkHttpClient,
        token: String,
        owner: String,
        repoName: String,
        branch: String,
        protocol: String,
        cleanCfIp: String
    ) {
        val body = JSONObject().apply {
            put("ref", branch)
            put("inputs", JSONObject().apply {
                put("protocol", protocol)
                put("clean_cf_ip", cleanCfIp)
                put("ws_path", "/separws")
                put("duration_minutes", "300")
            })
        }
        val req = requestBuilder(
            "https://api.github.com/repos/$owner/$repoName/actions/workflows/vpn-server-builder.yml/dispatches",
            token
        ).post(body.toString().toRequestBody(JSON_MEDIA)).build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful && resp.code != 204) {
                throw IllegalStateException("خطا در شروع GitHub Actions (HTTP ${resp.code}): ${extractGitHubError(raw)}")
            }
        }
    }

    private fun findLatestDispatchRun(
        client: OkHttpClient,
        token: String,
        owner: String,
        repoName: String,
        minCreatedAtMs: Long
    ): Triple<Long, String, Pair<String, String>>? {
        val req = requestBuilder(
            "https://api.github.com/repos/$owner/$repoName/actions/workflows/vpn-server-builder.yml/runs?event=workflow_dispatch&per_page=5",
            token
        ).get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val runs = JSONObject(resp.body?.string().orEmpty()).optJSONArray("workflow_runs") ?: return@runCatching null
                for (i in 0 until runs.length()) {
                    val r = runs.optJSONObject(i) ?: continue
                    val id = r.optLong("id", 0L)
                    val status = r.optString("status")
                    val conclusion = r.optString("conclusion")
                    val htmlUrl = r.optString("html_url")
                    val createdIso = r.optString("created_at")
                    val createdMs = parseIsoUtcMillis(createdIso)
                    if (id > 0L && (createdMs == 0L || createdMs >= minCreatedAtMs)) {
                        return@runCatching Triple(id, status, conclusion to htmlUrl)
                    }
                }
                null
            }
        }.getOrNull()
    }

    private suspend fun readLiveBranchNode(
        client: OkHttpClient,
        token: String?,
        owner: String,
        repoName: String,
        expectedRunId: Long?,
        minTimestampMs: Long,
        workflowHtmlUrl: String
    ): GitHubLiveServerResult? {
        val req = requestBuilder(
            "https://api.github.com/repos/$owner/$repoName/contents/$LIVE_JSON_PATH?ref=$LIVE_BRANCH",
            token
        ).get().build()
        val liveJson = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val wrapper = JSONObject(resp.body?.string().orEmpty())
                val contentB64 = wrapper.optString("content").replace("\n", "")
                if (contentB64.isBlank()) return@runCatching null
                JSONObject(String(Base64.decode(contentB64, Base64.DEFAULT), Charsets.UTF_8))
            }
        }.getOrNull() ?: return null

        val runIdStr = liveJson.optString("run_id")
        val runId = runIdStr.toLongOrNull()
        if (expectedRunId != null && runId != null && runId != expectedRunId) {
            return null
        }
        val startedMs = parseIsoUtcMillis(liveJson.optString("started_at_utc"))
        if (minTimestampMs > 0L && startedMs > 0L && startedMs < minTimestampMs) {
            return null
        }

        val cleanUri = liveJson.optString("clean_uri").trim()
        val directUri = liveJson.optString("direct_uri").trim()
        val combined = listOf(cleanUri, directUri).filter { it.isNotBlank() }.joinToString("\n")
        if (combined.isBlank()) return null

        val rawSubUrl = "https://raw.githubusercontent.com/$owner/$repoName/$LIVE_BRANCH/$LIVE_SUB_PATH"
        val parsed = ConfigUriParser.parseBulk(combined, subscriptionSource = rawSubUrl)
        if (parsed.isEmpty()) return null

        val tested = parsed.map { p ->
            val ms = NetworkProbe.measureTcpLatency(p.host, p.port, timeoutMs = 2500)
            p.copy(latencyMs = ms, lastTestedAt = System.currentTimeMillis())
        }

        return GitHubLiveServerResult(
            runId = runId,
            repoFullName = "$owner/$repoName",
            protocol = liveJson.optString("protocol", "vless-ws"),
            tunnelHost = liveJson.optString("tunnel_host", ""),
            profiles = tested,
            rawSubscriptionUrl = rawSubUrl,
            workflowHtmlUrl = workflowHtmlUrl
        )
    }

    private fun parseIsoUtcMillis(iso: String): Long {
        if (iso.isBlank()) return 0L
        return runCatching {
            val clean = iso.replace("Z", "+0000").replace(Regex("\\.\\d+"), "")
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
            fmt.parse(clean)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun extractGitHubError(rawBody: String): String {
        return runCatching {
            JSONObject(rawBody).optString("message").ifBlank { rawBody.take(140) }
        }.getOrDefault(rawBody.take(140))
    }

    val WORKFLOW_YAML = """
name: SeparNet VPN Server Builder

on:
  workflow_dispatch:
    inputs:
      protocol:
        description: 'Tunnel protocol (vless-ws, vmess-ws, trojan-ws)'
        required: false
        default: 'vless-ws'
        type: choice
        options:
          - vless-ws
          - vmess-ws
          - trojan-ws
      clean_cf_ip:
        description: 'Cloudflare Clean Anycast IP (leave blank to use trycloudflare domain directly)'
        required: false
        default: '162.159.192.1'
        type: string
      ws_path:
        description: 'WebSocket path'
        required: false
        default: '/separws'
        type: string
      duration_minutes:
        description: 'Keep VPN server alive duration in minutes (max 330)'
        required: false
        default: '300'
        type: string

permissions:
  contents: write

jobs:
  vpn-server:
    runs-on: ubuntu-latest
    timeout-minutes: 350

    steps:
      - name: Checkout repository
        uses: actions/checkout@v7
        with:
          fetch-depth: 1

      - name: Install sing-box and cloudflared
        run: |
          set -e
          mkdir -p /tmp/vpn-bin
          SB_URL=${'$'}(curl -fsSL -H "Authorization: Bearer ${'$'}{{ github.token }}" https://api.github.com/repos/SagerNet/sing-box/releases/latest \
            | python3 -c "import sys,json; data=json.load(sys.stdin); print(next(a['browser_download_url'] for a in data['assets'] if 'linux-amd64.tar.gz' in a['name']))")
          curl -fsSL "${'$'}SB_URL" -o /tmp/sing-box.tar.gz
          tar -xzf /tmp/sing-box.tar.gz -C /tmp/vpn-bin --strip-components=1
          chmod +x /tmp/vpn-bin/sing-box

          curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 \
            -o /tmp/vpn-bin/cloudflared
          chmod +x /tmp/vpn-bin/cloudflared

      - name: Start sing-box + Cloudflare tunnel & publish node
        id: server
        env:
          INPUT_PROTOCOL: ${'$'}{{ inputs.protocol || 'vless-ws' }}
          INPUT_CLEAN_IP: ${'$'}{{ inputs.clean_cf_ip || '162.159.192.1' }}
          INPUT_WS_PATH: ${'$'}{{ inputs.ws_path || '/separws' }}
          INPUT_DURATION: ${'$'}{{ inputs.duration_minutes || '300' }}
        run: |
          set -e
          UUID=${'$'}(cat /proc/sys/kernel/random/uuid)
          PASS=${'$'}(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)
          WS_PATH="${'$'}{INPUT_WS_PATH:-/separws}"
          PROTO="${'$'}{INPUT_PROTOCOL:-vless-ws}"
          CLEAN_IP="${'$'}{INPUT_CLEAN_IP:-162.159.192.1}"

          python3 - <<PY
          import json

          proto = "${'$'}PROTO"
          uuid = "${'$'}UUID"
          password = "${'$'}PASS"
          ws_path = "${'$'}WS_PATH"

          if proto == "vmess-ws":
              inbound = {
                  "type": "vmess",
                  "tag": "vpn-in",
                  "listen": "127.0.0.1",
                  "listen_port": 8080,
                  "users": [{"uuid": uuid, "alterId": 0}],
                  "transport": {"type": "ws", "path": ws_path}
              }
          elif proto == "trojan-ws":
              inbound = {
                  "type": "trojan",
                  "tag": "vpn-in",
                  "listen": "127.0.0.1",
                  "listen_port": 8080,
                  "users": [{"password": password}],
                  "transport": {"type": "ws", "path": ws_path}
              }
          else:
              inbound = {
                  "type": "vless",
                  "tag": "vpn-in",
                  "listen": "127.0.0.1",
                  "listen_port": 8080,
                  "users": [{"uuid": uuid}],
                  "transport": {"type": "ws", "path": ws_path}
              }

          cfg = {
              "log": {"level": "warn"},
              "dns": {"servers": [{"tag": "cf", "address": "1.1.1.1"}]},
              "inbounds": [inbound],
              "outbounds": [{"type": "direct", "tag": "direct"}]
          }
          with open("/tmp/sb-server.json", "w") as f:
              json.dump(cfg, f, indent=2)
          PY

          /tmp/vpn-bin/sing-box run -c /tmp/sb-server.json > /tmp/singbox.log 2>&1 &
          SB_PID=${'$'}!

          /tmp/vpn-bin/cloudflared tunnel --no-autoupdate --url http://127.0.0.1:8080 > /tmp/cf.log 2>&1 &
          CF_PID=${'$'}!

          CF_HOST=""
          for i in ${'$'}(seq 1 45); do
            CF_HOST=${'$'}(grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' /tmp/cf.log | head -n1 | sed 's|https://||') || true
            if [ -n "${'$'}CF_HOST" ]; then
              break
            fi
            sleep 1
          done

          if [ -z "${'$'}CF_HOST" ]; then
            cat /tmp/cf.log
            exit 1
          fi

          python3 - <<PY
          import base64, json, urllib.parse
          from datetime import datetime, timezone, timedelta

          proto = "${'$'}PROTO"
          uuid = "${'$'}UUID"
          password = "${'$'}PASS"
          ws_path = "${'$'}WS_PATH"
          cf_host = "${'$'}CF_HOST"
          clean_ip = "${'$'}CLEAN_IP".strip() or cf_host
          dur_min = max(5, min(330, int("${'$'}INPUT_DURATION" or "300")))
          enc_path = urllib.parse.quote(ws_path, safe="")

          if proto == "vmess-ws":
              def make_vmess(addr, tag):
                  obj = {
                      "v": "2", "ps": tag, "add": addr, "port": "443",
                      "id": uuid, "aid": "0", "net": "ws", "type": "none",
                      "host": cf_host, "path": ws_path, "tls": "tls", "sni": cf_host
                  }
                  return "vmess://" + base64.b64encode(json.dumps(obj).encode()).decode()
              clean_uri = make_vmess(clean_ip, "SeparNet-GitHub-CleanIP")
              direct_uri = make_vmess(cf_host, "SeparNet-GitHub-Direct")
          elif proto == "trojan-ws":
              clean_uri = f"trojan://{password}@{clean_ip}:443?security=tls&sni={cf_host}&fp=chrome&type=ws&host={cf_host}&path={enc_path}#SeparNet-GitHub-CleanIP"
              direct_uri = f"trojan://{password}@{cf_host}:443?security=tls&sni={cf_host}&fp=chrome&type=ws&host={cf_host}&path={enc_path}#SeparNet-GitHub-Direct"
          else:
              clean_uri = f"vless://{uuid}@{clean_ip}:443?encryption=none&security=tls&sni={cf_host}&fp=chrome&type=ws&host={cf_host}&path={enc_path}#SeparNet-GitHub-CleanIP"
              direct_uri = f"vless://{uuid}@{cf_host}:443?encryption=none&security=tls&sni={cf_host}&fp=chrome&type=ws&host={cf_host}&path={enc_path}#SeparNet-GitHub-Direct"

          now = datetime.now(timezone.utc)
          exp = now + timedelta(minutes=dur_min)
          payload = {
              "status": "running",
              "run_id": "${'$'}{{ github.run_id }}",
              "protocol": proto,
              "tunnel_host": cf_host,
              "clean_ip": clean_ip,
              "clean_uri": clean_uri,
              "direct_uri": direct_uri,
              "started_at_utc": now.isoformat(),
              "expires_at_utc": exp.isoformat()
          }
          with open("/tmp/github-vpn-live.json", "w") as f:
              json.dump(payload, f, indent=2)
          with open("/tmp/github-vpn-sub.txt", "w") as f:
              f.write(clean_uri + "\n" + direct_uri + "\n")
          PY

          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git checkout --orphan vpn-live-tmp
          git rm -rf . >/dev/null 2>&1 || true
          cp /tmp/github-vpn-live.json github-vpn-live.json
          cp /tmp/github-vpn-sub.txt github-vpn-sub.txt
          git add github-vpn-live.json github-vpn-sub.txt
          git commit -m "Publish live GitHub VPN server (${'$'}{{ github.run_id }})"
          git push origin HEAD:refs/heads/vpn-live --force

          {
            echo "## SeparNet GitHub VPN Server Live"
            echo ""
            echo "- **Protocol:** \`${'$'}PROTO\`"
            echo "- **Tunnel Host:** \`${'$'}CF_HOST\`"
            echo "- **Clean CF IP:** \`${'$'}CLEAN_IP\`"
            echo ""
            echo "### Copy Ready Configs"
            echo '```text'
            cat /tmp/github-vpn-sub.txt
            echo '```'
          } >> "${'$'}GITHUB_STEP_SUMMARY"

          END_EPOCH=${'$'}(( ${'$'}(date +%s) + (${'$'}{INPUT_DURATION:-300} * 60) ))
          while [ ${'$'}(date +%s) -lt "${'$'}END_EPOCH" ]; do
            if ! kill -0 "${'$'}SB_PID" 2>/dev/null || ! kill -0 "${'$'}CF_PID" 2>/dev/null; then
              echo "Tunnel daemon exited unexpectedly"
              exit 1
            fi
            sleep 30
          done
    """.trimIndent()
}
