#!/bin/bash
# ==============================================================================
# remote-gradlew.sh
# ==============================================================================
# 手元のLinuxのコード変更を Mac mini に同期し、リモートでビルドして成果物を戻すスクリプト。
# ローカルで `./gradlew <tasks>` と叩くのと同様に動作します。
# ------------------------------------------------------------------------------

set -e

REMOTE_HOST="mac"
REMOTE_DIR="~/JapneseKeyboard-remote"
MAC_SDK_DIR="/Users/shibadogcap/Library/Android/sdk"

# 同期から除外するファイル（ビルドキャッシュやローカル設定）
EXCLUDES=(
    --exclude='.gradle/'
    --exclude='.idea/'
    --exclude='.git/'
    --exclude='build/'
    --exclude='*/build/'
    --exclude='local.properties'
)

echo "=== [1/3] Mac mini (${REMOTE_HOST}) への差分コード同期中... ==="

# リモートディレクトリの作成
ssh -o ConnectTimeout=30 "$REMOTE_HOST" "mkdir -p $REMOTE_DIR"

# rsyncで高速ファイル同期
rsync -avz --delete "${EXCLUDES[@]}" ./ "$REMOTE_HOST:$REMOTE_DIR/"

# Mac mini 側に専用の local.properties を配置する
ssh -o ConnectTimeout=30 "$REMOTE_HOST" "echo 'sdk.dir=${MAC_SDK_DIR}' > ${REMOTE_DIR}/local.properties"

echo "=== [2/3] Mac mini 上でビルド実行中... ==="

# SSH経由でビルドを実行
# Mac mini の zsh 環境（PATHやJAVA_HOME）を読み込むため、インタラクティブシェルで実行します。
ssh -o ConnectTimeout=30 -t "$REMOTE_HOST" "source ~/.zshrc && cd $REMOTE_DIR && ./gradlew $@"

echo "=== [3/3] ビルド成果物（APK）を手元のLinuxへ同期中... ==="

# リモートで生成されたAPKをローカルに書き戻す
# app/build/outputs 配下にビルド成果物ができるため、その差分をローカルに反映します。
mkdir -p ./app/build/outputs
rsync -avz "$REMOTE_HOST:$REMOTE_DIR/app/build/outputs/" ./app/build/outputs/

echo "=== 🎉 リモートビルド同期完了！ ==="
