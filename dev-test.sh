#!/bin/bash
set -e

# Build script for all MC versions
# Uses host Gradle for building, Docker with appropriate JDK version for server testing
# Java version is read from versionProperties/<version>.properties (java_version property)
# Pass --debug (and optional --debug-port/--debug-suspend) to forward a JDWP port out of the
# Docker container so VS Code can attach to the server process while it is running.

# Default to building/testing both legacy 1.21.x and the new 1.21.6+ profile
# MC_VERSIONS=("1.12" "1.13" "1.14" "1.15" "1.16" "1.17" "1.18" "1.19" "1.20" "1.21" "1.21.6")
MC_VERSIONS=("1.21" "1.21.6")
BUILD_DIR="build/forgix"
OUTPUT_DIR="build/all-versions"
TEST_DIR="build/server-test"

MCUTILS_API="https://mcutils.com/api/server-jars"
MODRINTH_API="https://api.modrinth.com/v2"

# Extract the version key for lookups.
# If a patch segment exists, return major.minor.patch (e.g., 1.21.6 -> 1.21.6),
# otherwise return major.minor (e.g., 1.20 -> 1.20).
get_major_minor_key() {
    local version=$1
    local IFS='.'
    read -r major minor patch _ <<< "$version"
    if [ -n "$patch" ]; then
        echo "${major}.${minor}.${patch}"
    elif [ -n "$minor" ]; then
        echo "${major}.${minor}"
    else
        echo "$major"
    fi
}

# Optional Docker debugging configuration
DEBUG_ENABLED=false
DEBUG_PORT="${SERVERCHAN_DEBUG_PORT:-5005}"
DEBUG_SUSPEND="${SERVERCHAN_DEBUG_SUSPEND:-n}"
JAVA_DEBUG_OPTS=""

# Check for CI mode
if [ -n "$SERVERCHAN_CI_API_KEY" ]; then
    CI_MODE=true
    echo "CI Mode enabled (SERVERCHAN_CI_API_KEY found)"
else
    CI_MODE=false
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# Get Java version from version properties file
get_java_version() {
    local version=$1
    local props_file="versionProperties/${version}.properties"
    if [ -f "$props_file" ]; then
        grep "^java_version=" "$props_file" | cut -d'=' -f2
    else
        echo "21"  # Default to Java 21
    fi
}

# Get Docker image for a specific Java version
get_docker_image() {
    local java_ver=$1
    echo "eclipse-temurin:${java_ver}-jdk"
}

# MC version mappings - beginning and latest for each major version
# Format: "beginning:latest" or just "version" if only one exists
declare -A MC_FULL_VERSIONS_FABRIC=(
    # Fabric starts at 1.14 (no support for 1.12/1.13)
    ["1.14"]="1.14.4"
    ["1.15"]="1.15.2"
    ["1.16"]="1.16.1:1.16.5"
    ["1.17"]="1.17:1.17.1"
    ["1.18"]="1.18:1.18.2"
    ["1.19"]="1.19:1.19.4"
    ["1.20"]="1.20:1.20.6"
    ["1.21"]="1.21:1.21.5"
    ["1.21.6"]="1.21.6:1.21.10"
)

declare -A MC_FULL_VERSIONS_FORGE=(
    # Forge 1.12-1.15 not supported with Architectury Loom
    ["1.16"]="1.16.1:1.16.5"
    ["1.17"]="1.17.1"  # Only 1.17.1 available for Forge
    ["1.18"]="1.18:1.18.2"
    ["1.19"]="1.19:1.19.4"
    ["1.20"]="1.20.6" # Only 1.20.6, other versions cause issues with Component.literal()
    ["1.21"]="1.21:1.21.5"
    ["1.21.6"]="1.21.6:1.21.10"
)

declare -A MC_FULL_VERSIONS_NEOFORGE=(
    ["1.20"]="1.20.1:1.20.6"  # NeoForge starts at 1.20.1
    ["1.21"]="1.21:1.21.5"    # Stable before 1.21.6+
    ["1.21.6"]="1.21.6:1.21.10"      # 1.21.6+ profile
)

declare -A MC_FULL_VERSIONS_PAPER=(
    ["1.12"]="1.12.2"
    ["1.13"]="1.13.2"
    ["1.14"]="1.14.4"
    ["1.15"]="1.15.2"
    ["1.16"]="1.16.1:1.16.5"
    ["1.17"]="1.17:1.17.1"
    ["1.18"]="1.18:1.18.2"
    ["1.19"]="1.19:1.19.4"
    ["1.20"]="1.20:1.20.6"
    ["1.21"]="1.21:1.21.5"
    ["1.21.6"]="1.21.6:1.21.10"
)

# Helper function to get version list from version string
get_version_list() {
    local version_str=$1
    if [[ "$version_str" == *":"* ]]; then
        echo "${version_str//:/ }"
    else
        echo "$version_str"
    fi
}

# Create directories
mkdir -p "$OUTPUT_DIR" "$TEST_DIR"

# Get loaders for a specific version from properties file
get_loaders_for_version() {
    local version=$1
    local props_file="versionProperties/${version}.properties"
    if [ -f "$props_file" ]; then
        grep "^builds_for=" "$props_file" | cut -d'=' -f2
    else
        echo ""
    fi
}

# Check if a loader is supported for a specific version
loader_supported_for_version() {
    local loader=$1
    local version=$2
    local loaders=$(get_loaders_for_version "$version")
    [[ ",$loaders," == *",$loader,"* ]]
}

# Build for each version
build_version() {
    local version=$1
    log_step "Building for MC $version..."

    if ./gradlew -PmcVer="$version" build mergeJars --no-daemon; then
        # First try merged jar in build/forgix
        local jar_file=$(ls "$BUILD_DIR"/serverchan-*-${version}-*.jar 2>/dev/null | head -1)

        # If not found, try individual loader directories
        if [ -z "$jar_file" ]; then
            local loaders=$(get_loaders_for_version "$version")
            for loader in ${loaders//,/ }; do
                jar_file=$(ls "${loader}/build/libs"/serverchan-*-${version}*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1)
                if [ -n "$jar_file" ]; then
                    log_info "Found jar in ${loader}/build/libs/"
                    break
                fi
            done
        fi

        if [ -n "$jar_file" ]; then
            cp "$jar_file" "$OUTPUT_DIR/serverchan-mc${version}.jar"
            log_info "Built: $OUTPUT_DIR/serverchan-mc${version}.jar"
            return 0
        else
            log_error "No jar found for MC $version"
            return 1
        fi
    else
        log_error "Build failed for MC $version"
        return 1
    fi
}

# Clean up Docker-created files (may be owned by root)
cleanup_server_dir() {
    local dir=$1
    if [ -d "$dir" ]; then
        # Use Docker to remove files created by Docker (handles root-owned files)
        docker run --rm -v "$(pwd)/$dir:/cleanup" alpine sh -c 'rm -rf /cleanup/* /cleanup/.[!.]*' 2>/dev/null || true
        rm -rf "$dir" 2>/dev/null || true
    fi
}

# Test with Fabric server in Docker
test_fabric() {
    local version=$1
    local version_key
    version_key=$(get_major_minor_key "$version")
    local version_str=${MC_FULL_VERSIONS_FABRIC[$version_key]}

    if [ -z "$version_str" ]; then
        log_warn "No Fabric versions defined for MC $version"
        return 1
    fi

    local versions=($(get_version_list "$version_str"))
    local all_passed=true

    for mc_full in "${versions[@]}"; do
        local server_dir="$TEST_DIR/fabric-$mc_full"

        log_step "Testing Fabric server for MC $mc_full..."

        cleanup_server_dir "$server_dir"
        mkdir -p "$server_dir/mods"

        # Download Fabric server
        log_info "Downloading Fabric server..."
        curl -sL -o "$server_dir/server.jar" "$MCUTILS_API/fabric/$mc_full/download"

        # Copy mod
        cp "$OUTPUT_DIR/serverchan-mc${version}.jar" "$server_dir/mods/"

        # Download Fabric API
        log_info "Downloading Fabric API..."
        local fabric_api_data=$(curl -s "$MODRINTH_API/project/fabric-api/version?game_versions=%5B%22$mc_full%22%5D&loaders=%5B%22fabric%22%5D" | head -c 100000)
        local fabric_api_url=$(echo "$fabric_api_data" | jq -r '(.[0].files[] | select(.primary == true) | .url) // .[0].files[0].url' 2>/dev/null)

        if [ -n "$fabric_api_url" ] && [ "$fabric_api_url" != "null" ]; then
            curl -sL -o "$server_dir/mods/fabric-api.jar" "$fabric_api_url"
        else
            log_warn "Could not download Fabric API for MC $mc_full"
        fi

        # Create eula.txt
        echo "eula=true" > "$server_dir/eula.txt"

        # Create server.properties
        cat > "$server_dir/server.properties" << 'EOF'
server-port=25565
online-mode=false
max-players=1
level-type=flat
generate-structures=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
EOF

        log_info "Server files:"
        ls -la "$server_dir/mods/"

        # Run server in Docker
        log_info "Starting Fabric server in Docker..."
        if ! run_server_docker "$server_dir" "Fabric $mc_full" "$version"; then
            all_passed=false
        fi
    done

    $all_passed
}

# Test with Paper server in Docker
test_paper() {
    local version=$1
    local version_key
    version_key=$(get_major_minor_key "$version")
    local version_str=${MC_FULL_VERSIONS_PAPER[$version_key]}

    if [ -z "$version_str" ]; then
        log_warn "No Paper versions defined for MC $version"
        return 1
    fi

    local versions=($(get_version_list "$version_str"))
    local all_passed=true

    for mc_full in "${versions[@]}"; do
        local server_dir="$TEST_DIR/paper-$mc_full"

        log_step "Testing Paper server for MC $mc_full..."

        cleanup_server_dir "$server_dir"
        mkdir -p "$server_dir/plugins"

        # Download Paper server
        log_info "Downloading Paper server..."
        curl -sL -o "$server_dir/server.jar" "$MCUTILS_API/paper/$mc_full/download"

        # Copy mod as plugin
        cp "$OUTPUT_DIR/serverchan-mc${version}.jar" "$server_dir/plugins/"

        # Create eula.txt
        echo "eula=true" > "$server_dir/eula.txt"

        # Create server.properties
        cat > "$server_dir/server.properties" << 'EOF'
server-port=25565
online-mode=false
max-players=1
level-type=flat
generate-structures=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
EOF

        log_info "Server files:"
        ls -la "$server_dir/plugins/"

        # Run server in Docker
        log_info "Starting Paper server in Docker..."
        if ! run_server_docker "$server_dir" "Paper $mc_full" "$version"; then
            all_passed=false
        fi
    done

    $all_passed
}

# Test with Forge server in Docker
test_forge() {
    local version=$1
    local version_key
    version_key=$(get_major_minor_key "$version")
    local version_str=${MC_FULL_VERSIONS_FORGE[$version_key]}

    if [ -z "$version_str" ]; then
        log_warn "No Forge versions defined for MC $version"
        return 1
    fi

    local versions=($(get_version_list "$version_str"))
    local all_passed=true

    for mc_full in "${versions[@]}"; do
        local server_dir="$TEST_DIR/forge-$mc_full"

        log_step "Testing Forge server for MC $mc_full..."

        cleanup_server_dir "$server_dir"
        mkdir -p "$server_dir/mods"

        # Download Forge installer
        log_info "Downloading Forge installer..."
        curl -sL -o "$server_dir/forge-installer.jar" "$MCUTILS_API/forge/$mc_full/download"

        # Install Forge (needs Docker for consistent Java)
        local java_ver=$(get_java_version "$version")
        local docker_image=$(get_docker_image "$java_ver")
        log_info "Installing Forge server with $docker_image..."
        if ! docker run --rm --user "$(id -u):$(id -g)" -v "$(pwd)/$server_dir:/server" -w /server "$docker_image" \
            java -jar forge-installer.jar --installServer; then
            log_error "Forge installer failed"
            return 1
        fi

        # Copy mod
        cp "$OUTPUT_DIR/serverchan-mc${version}.jar" "$server_dir/mods/"

        # Create eula.txt
        echo "eula=true" > "$server_dir/eula.txt"

        # Create server.properties
        cat > "$server_dir/server.properties" << 'EOF'
server-port=25565
online-mode=false
max-players=1
level-type=flat
generate-structures=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
EOF

        log_info "Server files:"
        ls -la "$server_dir/"

        # Run Forge server in Docker
        log_info "Starting Forge server in Docker..."
        if ! run_forge_docker "$server_dir" "Forge $mc_full" "$version"; then
            all_passed=false
        fi
    done

    $all_passed
}

# Test with NeoForge server in Docker
test_neoforge() {
    local version=$1
    local version_key
    version_key=$(get_major_minor_key "$version")
    local version_str=${MC_FULL_VERSIONS_NEOFORGE[$version_key]}

    if [ -z "$version_str" ]; then
        log_warn "No NeoForge versions defined for MC $version (NeoForge only supports 1.20+)"
        return 0  # Not a failure, just not available
    fi

    local versions=($(get_version_list "$version_str"))
    local all_passed=true

    for mc_full in "${versions[@]}"; do
        local server_dir="$TEST_DIR/neoforge-$mc_full"

        log_step "Testing NeoForge server for MC $mc_full..."

        cleanup_server_dir "$server_dir"
        mkdir -p "$server_dir/mods"

        # Download NeoForge installer from official Maven
        log_info "Downloading NeoForge installer..."

        # Map MC version to NeoForge version prefix
        local neoforge_prefix=""
        case "$mc_full" in
            "1.20.1") neoforge_prefix="20.2" ;;
            "1.20.4") neoforge_prefix="20.4" ;;
            "1.20.6") neoforge_prefix="20.6" ;;
            "1.21")   neoforge_prefix="21.0" ;;
            "1.21.1") neoforge_prefix="21.1" ;;
            "1.21.3") neoforge_prefix="21.3" ;;
            "1.21.10") neoforge_prefix="21.10" ;;
            *)
                log_error "Unsupported MC version for NeoForge: $mc_full"
                return 1
                ;;
        esac

        # Get latest stable NeoForge version for this MC version
        local all_versions=$(curl -s "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge")
        local neoforge_version=$(echo "$all_versions" | jq -r '.versions[]' | grep "^${neoforge_prefix}\." | grep -v beta | tail -1)

        if [ -z "$neoforge_version" ]; then
            log_error "Could not find NeoForge version for MC $mc_full (prefix: $neoforge_prefix)"
            return 1
        fi

        log_info "Found NeoForge version: $neoforge_version"
        local download_url="https://maven.neoforged.net/releases/net/neoforged/neoforge/${neoforge_version}/neoforge-${neoforge_version}-installer.jar"
        curl -L --fail --retry 3 -o "$server_dir/neoforge-installer.jar" "$download_url"

        # Verify download - check file size and integrity
        local file_size=$(stat -c%s "$server_dir/neoforge-installer.jar" 2>/dev/null || stat -f%z "$server_dir/neoforge-installer.jar" 2>/dev/null)
        if [ "$file_size" -lt 2000000 ]; then
            log_error "Downloaded file is too small ($file_size bytes), expected >2MB"
            return 1
        fi
        # Verify jar integrity
        if ! unzip -t "$server_dir/neoforge-installer.jar" >/dev/null 2>&1; then
            log_error "Downloaded NeoForge installer is corrupted"
            return 1
        fi
        log_info "Downloaded NeoForge installer: $file_size bytes (verified)"

        # Install NeoForge (needs Docker for consistent Java)
        local java_ver=$(get_java_version "$version")
        local docker_image=$(get_docker_image "$java_ver")
        log_info "Installing NeoForge server with $docker_image..."
        if ! docker run --rm --user "$(id -u):$(id -g)" -v "$(pwd)/$server_dir:/server" -w /server "$docker_image" \
            java -jar neoforge-installer.jar --installServer; then
            log_error "NeoForge installer failed"
            return 1
        fi

        # Verify installation created necessary files
        if [ ! -f "$server_dir/run.sh" ] && [ ! -d "$server_dir/libraries" ]; then
            log_error "NeoForge installation incomplete - no run.sh or libraries directory"
            return 1
        fi

        # Copy mod
        cp "$OUTPUT_DIR/serverchan-mc${version}.jar" "$server_dir/mods/"

        # Create eula.txt
        echo "eula=true" > "$server_dir/eula.txt"

        # Create server.properties
        cat > "$server_dir/server.properties" << 'EOF'
server-port=25565
online-mode=false
max-players=1
level-type=flat
generate-structures=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
EOF

        log_info "Server files:"
        ls -la "$server_dir/"

        # Run NeoForge server in Docker (same as Forge)
        log_info "Starting NeoForge server in Docker..."
        if ! run_neoforge_docker "$server_dir" "NeoForge $mc_full" "$version"; then
            all_passed=false
        fi
    done

    $all_passed
}

# Run server in Docker and check for successful start
run_server_docker() {
    local server_dir=$1
    local name=$2
    local mc_version=$3
    local timeout=180
    local java_ver=$(get_java_version "$mc_version")
    local docker_image=$(get_docker_image "$java_ver")

    local run_timeout=$timeout
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        run_timeout=3600
    fi

    log_info "Using Docker image: $docker_image (Java $java_ver)"

    local -a env_args=()
    if [ "$CI_MODE" = true ]; then
        env_args+=("-e" "SERVERCHAN_CI_API_KEY=$SERVERCHAN_CI_API_KEY")
    fi
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        env_args+=("-e" "JAVA_TOOL_OPTIONS=$JAVA_DEBUG_OPTS")
    fi

    local -a port_args=()
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        port_args+=("-p" "${DEBUG_PORT}:${DEBUG_PORT}")
    fi

    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        log_info "Forwarding debug port ${DEBUG_PORT} for $name container"
    fi

    docker run --rm --user "$(id -u):$(id -g)" -v "$(pwd)/$server_dir:/server" -w /server \
        --name "mc-test-$$" \
        "${port_args[@]}" \
        "${env_args[@]}" \
        "$docker_image" \
        timeout ${run_timeout}s java -Xmx2G -Xms1G -jar server.jar nogui 2>&1 | tee "$server_dir/output.log" &

    local docker_pid=$!
    local elapsed=0
    local wait_timeout=$run_timeout

    while [ $elapsed -lt $wait_timeout ]; do
        if [ "$CI_MODE" = true ]; then
             if grep -q "ci pass" "$server_dir/output.log" 2>/dev/null; then
                log_info "$name server CI check passed!"
                docker stop "mc-test-$$" 2>/dev/null || true
                wait $docker_pid 2>/dev/null || true
                return 0
             fi
        else
            # Match the actual server Done message, not "Done remapping"
            if grep -qE "Done \([0-9]+\.[0-9]+s\)!" "$server_dir/output.log" 2>/dev/null; then
                log_info "$name server started successfully!"
                docker stop "mc-test-$$" 2>/dev/null || true
                wait $docker_pid 2>/dev/null || true

                if grep -q "ServerChan is online" "$server_dir/output.log"; then
                    log_info "ServerChan mod loaded successfully!"
                    return 0
                else
                    log_warn "Could not confirm ServerChan mod loaded"
                    return 0
                fi
            fi
        fi

        sleep 2
        elapsed=$((elapsed + 2))
    done

    log_error "$name server startup timed out"
    docker stop "mc-test-$$" 2>/dev/null || true
    wait $docker_pid 2>/dev/null || true
    return 1
}

# Run Forge server (different startup method)
run_forge_docker() {
    local server_dir=$1
    local name=$2
    local mc_version=$3
    local timeout=300
    local java_ver=$(get_java_version "$mc_version")
    local docker_image=$(get_docker_image "$java_ver")

    local run_timeout=$timeout
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        run_timeout=3600
    fi

    log_info "Using Docker image: $docker_image (Java $java_ver)"

    # Create startup script for Forge
    cat > "$server_dir/start.sh" << 'SCRIPT'
#!/bin/bash
cd /server
if [ -f "run.sh" ]; then
    chmod +x run.sh && ./run.sh
elif ls forge-*.jar 1>/dev/null 2>&1; then
    FORGE_JAR=$(ls forge-*.jar 2>/dev/null | grep -v installer | head -1)
    java -Xmx2G -Xms1G -jar "$FORGE_JAR" nogui
elif [ -d "libraries/net/minecraftforge/forge" ]; then
    ARGS_FILE=$(find libraries -name "unix_args.txt" | head -1)
    if [ -n "$ARGS_FILE" ]; then
        java -Xmx2G -Xms1G @"$ARGS_FILE" nogui
    fi
else
    echo "Could not find Forge startup method"
    ls -la
    exit 1
fi
SCRIPT
    chmod +x "$server_dir/start.sh"

    local -a env_args=()
    if [ "$CI_MODE" = true ]; then
        env_args+=("-e" "SERVERCHAN_CI_API_KEY=$SERVERCHAN_CI_API_KEY")
    fi
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        env_args+=("-e" "JAVA_TOOL_OPTIONS=$JAVA_DEBUG_OPTS")
    fi

    local -a port_args=()
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        port_args+=("-p" "${DEBUG_PORT}:${DEBUG_PORT}")
    fi

    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        log_info "Forwarding debug port ${DEBUG_PORT} for $name container"
    fi

    docker run --rm --user "$(id -u):$(id -g)" -v "$(pwd)/$server_dir:/server" -w /server \
        --name "mc-test-$$" \
        "${port_args[@]}" \
        "${env_args[@]}" \
        "$docker_image" \
        timeout ${run_timeout}s /bin/bash /server/start.sh 2>&1 | tee "$server_dir/output.log" &

    local docker_pid=$!
    local elapsed=0
    local wait_timeout=$run_timeout

    while [ $elapsed -lt $wait_timeout ]; do
        if [ "$CI_MODE" = true ]; then
             if grep -q "ci pass" "$server_dir/output.log" 2>/dev/null; then
                log_info "$name server CI check passed!"
                docker stop "mc-test-$$" 2>/dev/null || true
                wait $docker_pid 2>/dev/null || true
                return 0
             fi
        else
            # Match the actual server Done message, not "Done remapping"
            if grep -qE "Done \([0-9]+\.[0-9]+s\)!" "$server_dir/output.log" 2>/dev/null; then
                log_info "$name server started successfully!"
                docker stop "mc-test-$$" 2>/dev/null || true
                wait $docker_pid 2>/dev/null || true

                if grep -q "ServerChan is online" "$server_dir/output.log"; then
                    log_info "ServerChan mod loaded successfully!"
                    return 0
                else
                    log_warn "Could not confirm ServerChan mod loaded"
                    return 0
                fi
            fi
        fi

        sleep 3
        elapsed=$((elapsed + 3))
    done

    log_error "$name server startup timed out"
    docker stop "mc-test-$$" 2>/dev/null || true
    wait $docker_pid 2>/dev/null || true
    return 1
}

# Run NeoForge server (similar to Forge but checks for neoforge paths)
run_neoforge_docker() {
    local server_dir=$1
    local name=$2
    local mc_version=$3
    local timeout=300
    local java_ver=$(get_java_version "$mc_version")
    local docker_image=$(get_docker_image "$java_ver")

    local run_timeout=$timeout
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        run_timeout=3600
    fi

    log_info "Using Docker image: $docker_image (Java $java_ver)"

    # Create startup script for NeoForge
    cat > "$server_dir/start.sh" << 'SCRIPT'
#!/bin/bash
cd /server
if [ -f "run.sh" ]; then
    chmod +x run.sh && ./run.sh
elif [ -d "libraries/net/neoforged/neoforge" ]; then
    ARGS_FILE=$(find libraries -name "unix_args.txt" | head -1)
    if [ -n "$ARGS_FILE" ]; then
        java -Xmx2G -Xms1G @"$ARGS_FILE" nogui
    fi
elif ls neoforge-*.jar 1>/dev/null 2>&1; then
    NEOFORGE_JAR=$(ls neoforge-*.jar 2>/dev/null | grep -v installer | head -1)
    java -Xmx2G -Xms1G -jar "$NEOFORGE_JAR" nogui
else
    echo "Could not find NeoForge startup method"
    ls -la
    exit 1
fi
SCRIPT
    chmod +x "$server_dir/start.sh"

    local -a env_args=()
    if [ "$CI_MODE" = true ]; then
        env_args+=("-e" "SERVERCHAN_CI_API_KEY=$SERVERCHAN_CI_API_KEY")
    fi
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        env_args+=("-e" "JAVA_TOOL_OPTIONS=$JAVA_DEBUG_OPTS")
    fi

    local -a port_args=()
    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        port_args+=("-p" "${DEBUG_PORT}:${DEBUG_PORT}")
    fi

    if [ -n "$JAVA_DEBUG_OPTS" ]; then
        log_info "Forwarding debug port ${DEBUG_PORT} for $name container"
    fi

    docker run --rm --user "$(id -u):$(id -g)" -v "$(pwd)/$server_dir:/server" -w /server \
        --name "mc-test-$$" \
        "${port_args[@]}" \
        "${env_args[@]}" \
        "$docker_image" \
        timeout ${run_timeout}s /bin/bash /server/start.sh 2>&1 | tee "$server_dir/output.log" &

    local docker_pid=$!
    local elapsed=0
    local wait_timeout=$run_timeout

    while [ $elapsed -lt $wait_timeout ]; do
        if [ "$CI_MODE" = true ]; then
             if grep -q "ci pass" "$server_dir/output.log" 2>/dev/null; then
                log_info "$name server CI check passed!"
                docker stop "mc-test-$$" 2>/dev/null || true
                wait $docker_pid 2>/dev/null || true
                return 0
             fi
        else
            # Match the actual server Done message, not "Done remapping"
            if grep -qE "Done \([0-9]+\.[0-9]+s\)!" "$server_dir/output.log" 2>/dev/null; then
                log_info "$name server started successfully!"
                docker stop "mc-test-$$" 2>/dev/null || true
                wait $docker_pid 2>/dev/null || true

                if grep -q "ServerChan is online" "$server_dir/output.log"; then
                    log_info "ServerChan mod loaded successfully!"
                    return 0
                else
                    log_warn "Could not confirm ServerChan mod loaded"
                    return 0
                fi
            fi
        fi

        sleep 3
        elapsed=$((elapsed + 3))
    done

    log_error "$name server startup timed out"
    docker stop "mc-test-$$" 2>/dev/null || true
    wait $docker_pid 2>/dev/null || true
    return 1
}

# Main execution
main() {
    local build_only=false
    local test_only=false
    local platform="all"

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --build-only) build_only=true; shift ;;
            --test-only) test_only=true; shift ;;
            --fabric) platform="fabric"; shift ;;
            --forge) platform="forge"; shift ;;
            --neoforge) platform="neoforge"; shift ;;
            --paper) platform="paper"; shift ;;
            --debug) DEBUG_ENABLED=true; shift ;;
            --debug-port)
                DEBUG_PORT="$2"
                shift 2
                ;;
            --debug-suspend)
                DEBUG_SUSPEND="$2"
                shift 2
                ;;
            *)
                if [ -f "versionProperties/$1.properties" ]; then
                    MC_VERSIONS=("$1")
                    shift
                else
                    echo "Unknown option: $1"
                    exit 1
                fi
                ;;
        esac
    done

    if [ "$DEBUG_ENABLED" = true ]; then
        JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=0.0.0.0:${DEBUG_PORT}"
        log_info "Docker debug mode enabled on port ${DEBUG_PORT} (suspend=${DEBUG_SUSPEND}). Attach with VS Code after the container starts."
    fi

    local failed=()
    local success=()

    # Build phase
    if [ "$test_only" = false ]; then
        log_info "Starting build for MC versions: ${MC_VERSIONS[*]}"
        echo "========================================"

        for version in "${MC_VERSIONS[@]}"; do
            echo ""
            if build_version "$version"; then
                success+=("build:$version")
            else
                failed+=("build:$version")
            fi
        done
    fi

    # Test phase
    if [ "$build_only" = false ] && command -v docker &> /dev/null; then
        echo ""
        echo "========================================"
        log_info "Starting server tests..."

        for version in "${MC_VERSIONS[@]}"; do
            if [ ! -f "$OUTPUT_DIR/serverchan-mc${version}.jar" ]; then
                log_warn "No jar for MC $version, skipping test"
                continue
            fi

            if { [ "$platform" = "all" ] || [ "$platform" = "fabric" ]; } && loader_supported_for_version "fabric" "$version"; then
                echo ""
                if test_fabric "$version"; then
                    success+=("fabric:$version")
                else
                    failed+=("fabric:$version")
                fi
            fi

            if { [ "$platform" = "all" ] || [ "$platform" = "forge" ]; } && loader_supported_for_version "forge" "$version"; then
                echo ""
                if test_forge "$version"; then
                    success+=("forge:$version")
                else
                    failed+=("forge:$version")
                fi
            fi

            if { [ "$platform" = "all" ] || [ "$platform" = "neoforge" ]; } && loader_supported_for_version "neoforge" "$version"; then
                echo ""
                if test_neoforge "$version"; then
                    success+=("neoforge:$version")
                else
                    failed+=("neoforge:$version")
                fi
            fi

            if { [ "$platform" = "all" ] || [ "$platform" = "paper" ]; } && loader_supported_for_version "spigot" "$version"; then
                echo ""
                if test_paper "$version"; then
                    success+=("paper:$version")
                else
                    failed+=("paper:$version")
                fi
            fi
        done
    elif [ "$build_only" = false ]; then
        log_warn "Docker not available, skipping server tests"
    fi

    # Summary
    echo ""
    echo "========================================"
    log_info "Summary:"
    echo "----------------------------------------"

    if [ ${#success[@]} -gt 0 ]; then
        log_info "Passed: ${success[*]}"
    fi

    if [ ${#failed[@]} -gt 0 ]; then
        log_error "Failed: ${failed[*]}"
    fi

    echo ""
    log_info "Output jars in: $OUTPUT_DIR/"
    ls -la "$OUTPUT_DIR/" 2>/dev/null || true

    if [ ${#failed[@]} -gt 0 ]; then
        exit 1
    fi
}

main "$@"
