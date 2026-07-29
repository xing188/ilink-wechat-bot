(function () {
    "use strict";

    // ===================== Three.js Cloud Effect =====================
    var container = document.getElementById("three-container");
    var scene = new THREE.Scene();
    var camera = new THREE.PerspectiveCamera(30, window.innerWidth / window.innerHeight, 1, 3000);
    var renderer = new THREE.WebGLRenderer({ antialias: false });
    renderer.setSize(window.innerWidth, window.innerHeight);
    container.appendChild(renderer.domElement);

    // Background gradient
    var bgCanvas = document.createElement("canvas");
    bgCanvas.width = 32;
    bgCanvas.height = window.innerHeight;
    var bgCtx = bgCanvas.getContext("2d");
    var gradient = bgCtx.createLinearGradient(0, 0, 0, bgCanvas.height);
    gradient.addColorStop(0, "#1e4877");
    gradient.addColorStop(0.5, "#4584b4");
    bgCtx.fillStyle = gradient;
    bgCtx.fillRect(0, 0, bgCanvas.width, bgCanvas.height);
    container.style.background = "url(" + bgCanvas.toDataURL("image/png") + ")";
    container.style.backgroundSize = "32px 100%";

    // Cloud texture
    var texture = THREE.ImageUtils.loadTexture("/cloud10.png", null, animate);
    texture.magFilter = THREE.LinearMipMapLinearFilter;
    texture.minFilter = THREE.LinearMipMapLinearFilter;

    var fog = new THREE.Fog(0x4584b4, -100, 3000);

    // Vertex shader
    var vertexShader = [
        "varying vec2 vUv;",
        "void main() {",
        "    vUv = uv;",
        "    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);",
        "}"
    ].join("\n");

    // Fragment shader
    var fragmentShader = [
        "uniform sampler2D map;",
        "uniform vec3 fogColor;",
        "uniform float fogNear;",
        "uniform float fogFar;",
        "varying vec2 vUv;",
        "void main() {",
        "    float depth = gl_FragCoord.z / gl_FragCoord.w;",
        "    float fogFactor = smoothstep(fogNear, fogFar, depth);",
        "    gl_FragColor = texture2D(map, vUv);",
        "    gl_FragColor.w *= pow(gl_FragCoord.z, 20.0);",
        "    gl_FragColor = mix(gl_FragColor, vec4(fogColor, gl_FragColor.w), fogFactor);",
        "}"
    ].join("\n");

    var material = new THREE.ShaderMaterial({
        uniforms: {
            "map": { type: "t", value: texture },
            "fogColor": { type: "c", value: fog.color },
            "fogNear": { type: "f", value: fog.near },
            "fogFar": { type: "f", value: fog.far }
        },
        vertexShader: vertexShader,
        fragmentShader: fragmentShader,
        depthWrite: false,
        depthTest: false,
        transparent: true
    });

    // Create cloud geometry
    var geometry = new THREE.Geometry();
    var plane = new THREE.Mesh(new THREE.PlaneGeometry(64, 64));

    for (var i = 0; i < 8000; i++) {
        plane.position.x = Math.random() * 1000 - 500;
        plane.position.y = -Math.random() * Math.random() * 200 - 15;
        plane.position.z = i;
        plane.rotation.z = Math.random() * Math.PI;
        plane.scale.x = plane.scale.y = Math.random() * Math.random() * 1.5 + 0.5;
        THREE.GeometryUtils.merge(geometry, plane);
    }

    var mesh = new THREE.Mesh(geometry, material);
    scene.add(mesh);

    mesh = new THREE.Mesh(geometry, material);
    mesh.position.z = -8000;
    scene.add(mesh);

    camera.position.z = 6000;

    // Mouse interaction
    var mouseX = 0, mouseY = 0;
    var windowHalfX = window.innerWidth / 2;
    var windowHalfY = window.innerHeight / 2;
    var _speedX = 0.25;
    var _speedY = 0.15;

    document.addEventListener("mousemove", function (e) {
        mouseX = (e.clientX - windowHalfX) * _speedX;
        mouseY = (e.clientY - windowHalfY) * _speedY;
    });

    var start_time = Date.now();

    function animate() {
        requestAnimationFrame(animate);

        var position = ((Date.now() - start_time) * 0.03) % 8000;

        camera.position.x += (mouseX - camera.position.x) * 0.01;
        camera.position.y += (-mouseY - camera.position.y) * 0.01;
        camera.position.z = -position + 8000;

        renderer.render(scene, camera);
    }

    animate();

    window.addEventListener("resize", function () {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    });

    // ===================== QR Code & Login Status =====================
    var qrCodeInstance = null;
    var statusDot = document.getElementById("statusDot");
    var statusLabel = document.getElementById("statusLabel");
    var qrOverlay = document.getElementById("qrOverlay");
    var statusText = document.getElementById("statusText");
    var statusIcon = document.getElementById("statusIcon");

    function updateUI(status, botId, errorMsg) {
        switch (status) {
            case "WAITING_SCAN":
                statusDot.className = "status-dot";
                statusLabel.textContent = "等待扫码...";
                if (qrOverlay) qrOverlay.className = "qr-overlay";
                break;

            case "SCANNING":
                statusDot.className = "status-dot";
                statusLabel.textContent = "已扫码，正在登录...";
                if (qrOverlay) {
                    qrOverlay.className = "qr-overlay visible";
                    statusIcon.innerHTML = '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>';
                    statusText.textContent = "登录中";
                }
                break;

            case "LOGGING_IN":
                statusDot.className = "status-dot";
                statusLabel.textContent = "正在登录...";
                if (qrOverlay) {
                    qrOverlay.className = "qr-overlay visible";
                    statusIcon.innerHTML = '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>';
                    statusText.textContent = "登录中";
                }
                break;

            case "SUCCESS":
                statusDot.className = "status-dot success";
                statusLabel.textContent = "登录成功！Bot ID: " + (botId || "unknown");
                if (qrOverlay) {
                    qrOverlay.className = "qr-overlay visible success";
                    statusIcon.innerHTML = '<circle cx="12" cy="12" r="10"/><path d="M9 12l2 2 4-4"/>';
                    statusText.textContent = "登录成功";
                }
                break;

            case "FAILED":
                statusDot.className = "status-dot failed";
                statusLabel.textContent = "登录失败: " + (errorMsg || "未知错误") + "，正在重试...";
                if (qrOverlay) {
                    qrOverlay.className = "qr-overlay visible";
                    statusIcon.innerHTML = '<circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>';
                    statusText.textContent = "登录失败，刷新中...";
                }
                qrCodeInstance = null;
                setTimeout(function() { pollStatus(); }, 3000);
                break;
        }
    }

    function renderQRCode(content) {
        var qrContainer = document.getElementById("qrcode");
        qrContainer.innerHTML = "";
        if (!content) return;

        try {
            qrCodeInstance = new QRCode(qrContainer, {
                text: content,
                width: 180,
                height: 180,
                colorDark: "#1a1a2e",
                colorLight: "#ffffff",
                correctLevel: QRCode.CorrectLevel.H,
            });
        } catch (e) {
            console.error("QR code render failed:", e);
        }
    }

    function pollStatus() {
        fetch("/api/login/status")
            .then(function (resp) { return resp.json(); })
            .then(function (data) {
                var status = data.status || "WAITING_SCAN";
                var qrContent = data.qrCodeContent || "";
                var botId = data.botId || "";
                var errorMsg = data.errorMessage || "";

                if (qrContent && !qrCodeInstance) {
                    renderQRCode(qrContent);
                }

                updateUI(status, botId, errorMsg);

                if (status !== "SUCCESS" && status !== "FAILED") {
                    setTimeout(pollStatus, 1500);
                } else {
                    setTimeout(pollStatus, 5000);
                }
            })
            .catch(function () {
                setTimeout(pollStatus, 2000);
            });
    }

    // Start polling after page load
    setTimeout(pollStatus, 500);
})();
