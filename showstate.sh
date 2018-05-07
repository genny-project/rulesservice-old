split() {
    local string="$1"
    local delimiter="$2"
    if [ -n "$string" ]; then
        local part
        while read -d "$delimiter" part; do
            echo $part
        done <<< "$string"
        echo $part
    fi
}

split $1 ":"
