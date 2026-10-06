import UIKit
import WebKit

/// 게임 페이지(GitHub Pages)를 화면 가득 띄우는 껍데기. 안드로이드 앱(MainActivity)과 같은 방식으로 움직인다.
/// - 켤 때 version.json 을 보고, 배포가 바뀌었으면 게임 페이지를 새로 받아 기기에 저장한다.
/// - 저장해 둔 사본을 원래 주소(origin)로 띄우므로 인터넷이 없어도 켜지고, 게임 저장(localStorage)도 그 주소에 묶여 남는다.
final class GameViewController: UIViewController, WKNavigationDelegate {
    private static let night = UIColor(red: 5 / 255, green: 7 / 255, blue: 15 / 255, alpha: 1)
    private static let siteVersionKey = "siteVersion"

    private let gameURL: URL = {
        let s = Bundle.main.object(forInfoDictionaryKey: "GameURL") as? String
        return URL(string: s ?? "https://wjsdbs75-afk.github.io/marine-kiugi/")!
    }()

    private lazy var gameFile: URL = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("game.html")
    }()

    private var web: WKWebView!
    private let loadingView = UIStackView()
    private let loadingText = UILabel()
    private let errorView = UIStackView()
    private let errorDetail = UILabel()
    private var startSeq = 0
    private var lastCrashAt: Date?

    // 상태 바는 그대로 두고 밝은 글자로. 아래 홈 막대는 잠시 뒤 흐려지게
    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
    override var prefersHomeIndicatorAutoHidden: Bool { true }
    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge { .bottom }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Self.night
        makeWebView()
        makeOverlays()
        start()
    }

    // ------------------------------------------------------------------ 화면

    private func makeWebView() {
        let cfg = WKWebViewConfiguration()
        cfg.websiteDataStore = .default()                   // 게임 저장(localStorage)이 앱을 껐다 켜도 남도록
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        // 길게 눌러도 글자 선택·확대 메뉴가 뜨지 않게
        let css = "html,body{-webkit-touch-callout:none;-webkit-user-select:none;user-select:none}"
        let js = "(function(){var s=document.createElement('style');s.textContent='\(css)';document.documentElement.appendChild(s);})();"
        cfg.userContentController.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true))

        let w = WKWebView(frame: view.bounds, configuration: cfg)
        w.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        w.navigationDelegate = self
        w.isOpaque = false
        w.backgroundColor = Self.night
        w.scrollView.backgroundColor = Self.night
        w.scrollView.isScrollEnabled = false
        w.scrollView.bounces = false
        w.scrollView.minimumZoomScale = 1                    // 두 번 탭·핀치로 화면이 확대되지 않게
        w.scrollView.maximumZoomScale = 1
        w.scrollView.pinchGestureRecognizer?.isEnabled = false
        w.scrollView.contentInsetAdjustmentBehavior = .never   // 노치·홈 막대 여백은 페이지가 env(safe-area-inset-*) 로 직접 민다
        w.allowsLinkPreview = false
        w.isHidden = true                                     // 다 뜬 뒤에 보여 준다
        view.addSubview(w)
        web = w
    }

    private func makeOverlays() {
        let spinner = UIActivityIndicatorView(style: .large)
        spinner.color = UIColor(red: 1, green: 0.76, blue: 0.12, alpha: 1)
        spinner.startAnimating()
        loadingText.textColor = UIColor(red: 0.66, green: 0.71, blue: 0.84, alpha: 1)
        loadingText.font = .systemFont(ofSize: 15, weight: .semibold)
        loadingView.axis = .vertical
        loadingView.alignment = .center
        loadingView.spacing = 14
        loadingView.addArrangedSubview(spinner)
        loadingView.addArrangedSubview(loadingText)

        let title = UILabel()
        title.text = "게임을 불러오지 못했어요"
        title.textColor = UIColor(red: 0.92, green: 0.94, blue: 1, alpha: 1)
        title.font = .systemFont(ofSize: 18, weight: .bold)
        errorDetail.textColor = UIColor(red: 0.66, green: 0.71, blue: 0.84, alpha: 1)
        errorDetail.font = .systemFont(ofSize: 14)
        errorDetail.numberOfLines = 0
        errorDetail.textAlignment = .center
        let retry = UIButton(type: .system)
        retry.setTitle("  다시 시도  ", for: .normal)
        retry.titleLabel?.font = .systemFont(ofSize: 16, weight: .bold)
        retry.setTitleColor(UIColor(red: 0.04, green: 0.06, blue: 0.11, alpha: 1), for: .normal)
        retry.backgroundColor = UIColor(red: 1, green: 0.76, blue: 0.12, alpha: 1)
        retry.layer.cornerRadius = 10
        retry.contentEdgeInsets = UIEdgeInsets(top: 10, left: 18, bottom: 10, right: 18)
        retry.addTarget(self, action: #selector(retryTapped), for: .touchUpInside)
        errorView.axis = .vertical
        errorView.alignment = .center
        errorView.spacing = 12
        errorView.addArrangedSubview(title)
        errorView.addArrangedSubview(errorDetail)
        errorView.addArrangedSubview(retry)
        errorView.isHidden = true

        for v in [loadingView, errorView] {
            v.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(v)
            NSLayoutConstraint.activate([
                v.centerXAnchor.constraint(equalTo: view.centerXAnchor),
                v.centerYAnchor.constraint(equalTo: view.centerYAnchor),
                v.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
                v.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),
            ])
        }
    }

    @objc private func retryTapped() { start() }

    private func showError(_ detail: String?) {
        loadingView.isHidden = true
        web.isHidden = true
        errorDetail.text = detail ?? ""
        errorView.isHidden = false
    }

    // ------------------------------------------------------------------ 새 버전 확인과 불러오기

    private func start() {
        startSeq += 1
        let seq = startSeq
        errorView.isHidden = true
        loadingText.text = ""
        loadingView.isHidden = false
        web.isHidden = true
        let file = gameFile, gameURL = self.gameURL
        Task.detached { [weak self] in
            var problem: String?
            let site = await Self.fetchSiteVersion(gameURL)   // nil: 서버에 닿지 못함, "": version.json 이 없음
            let have = FileManager.default.fileExists(atPath: file.path)
            if site == nil {
                problem = "인터넷 연결을 확인한 뒤 다시 시도해 주세요."
            } else if !have || site!.isEmpty || site != UserDefaults.standard.string(forKey: Self.siteVersionKey) {
                await MainActor.run {
                    if self?.startSeq == seq { self?.loadingText.text = have ? "새 버전을 받는 중…" : "게임을 받는 중…" }
                }
                problem = await Self.download(gameURL, to: file)
                if problem == nil { UserDefaults.standard.set(site, forKey: Self.siteVersionKey) }
            }
            let html = try? String(contentsOf: file, encoding: .utf8)
            await MainActor.run {
                guard let self, seq == self.startSeq else { return }
                if let html {
                    self.web.loadHTMLString(html, baseURL: self.gameURL)
                } else {
                    self.showError(problem)
                }
            }
        }
    }

    private static func session(_ timeout: TimeInterval) -> URLSession {
        let c = URLSessionConfiguration.ephemeral
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.timeoutIntervalForRequest = timeout
        c.timeoutIntervalForResource = timeout * 3
        return URLSession(configuration: c)
    }

    /// 배포할 때마다 바뀌는 작은 파일(version.json)의 내용.
    private static func fetchSiteVersion(_ base: URL) async -> String? {
        let ms = Int(Date().timeIntervalSince1970 * 1000)
        guard let url = URL(string: "version.json?t=\(ms)", relativeTo: base) else { return "" }
        do {
            let (data, resp) = try await session(4).data(from: url)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else { return "" }
            return String(decoding: data.prefix(4096), as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        } catch {
            return nil
        }
    }

    /// 게임 페이지를 받아 저장한다. 다 받아 온전한지 확인한 뒤에만 바꿔 쓰므로 실패해도 기존 사본은 그대로다.
    private static func download(_ base: URL, to dest: URL) async -> String? {
        let ms = Int(Date().timeIntervalSince1970 * 1000)
        guard let url = URL(string: "?t=\(ms)", relativeTo: base) else { return "주소가 잘못되었습니다." }
        do {
            let (data, resp) = try await session(20).data(from: url)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            guard code == 200 else { return "\(base.absoluteString)\nHTTP \(code)" }
            let tail = String(decoding: data.suffix(64), as: UTF8.self).lowercased()
            guard data.count >= 1024, tail.contains("</html>") else { return "받은 페이지가 온전하지 않습니다." }
            try data.write(to: dest, options: .atomic)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    // ------------------------------------------------------------------ 웹 화면

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        let seq = startSeq
        webView.evaluateJavaScript("typeof window.__mkBack === 'function'") { [weak self] value, _ in
            guard let self, seq == self.startSeq else { return }
            self.loadingView.isHidden = true
            self.web.isHidden = false
            if (value as? Bool) != true {
                // 게임이 아닌 것이 떴다. 다음에 켤 때 다시 받도록 "받았다"는 표시를 지운다
                UserDefaults.standard.removeObject(forKey: Self.siteVersionKey)
            }
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        showError(error.localizedDescription)
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        // 게임 밖으로 나가는 링크는 사파리로 넘긴다
        if action.navigationType == .linkActivated, let url = action.request.url, url.host != gameURL.host {
            UIApplication.shared.open(url)
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        // 메모리가 모자라 웹 화면이 죽은 경우: 다시 띄운다. 켜자마자 또 죽으면 오류 화면에서 멈춘다
        let now = Date()
        if let last = lastCrashAt, now.timeIntervalSince(last) < 15 {
            showError("메모리가 부족해 게임 화면이 닫혔습니다.")
        } else {
            start()
        }
        lastCrashAt = now
    }
}
