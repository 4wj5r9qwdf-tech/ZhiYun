#!/usr/bin/env bash
# 文件用途：执行“prepare-snail-document-key.sh”对应的本地开发、诊断或数据核验辅助操作。
# 文件版本：1.0.0
# 功能目的：执行“prepare-snail-document-key.sh”对应的本地开发、诊断或数据核验辅助操作。
# 设计思路：将可重复环境操作收敛到脚本，依赖环境变量并避免凭据进入代码或输出。
# 实现方法：检查参数和本地环境，再执行目标命令或只读核验并返回明确退出状态。
# 关键约束：不输出凭据、不操作生产数据、不执行无关破坏性操作。
# 依赖与引用：Docs/规范/开发环境与同步流程.md；Docs/规范/部署与迁移.md
# 初始开发人员：JY（织云项目自研内容）
# 初始开发时间：待追溯（不以补录时间代替原始开发时间）
# 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
# 最近修改人员：JY
# AI辅助工具：Codex（智能体）
# 最近修改时间：2026-11-20
# 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
# 最近测试时间：本次整理未执行功能测试
# 测试人员：本次整理未执行
# 测试状态：待功能回归；历史测试记录待追溯
# 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
# 为同机的 SnailAI 和执行进程准备共享签名密钥；不输出或覆盖已有密钥。
set -euo pipefail
key_file="${SNAIL_AI_DOCUMENT_BRIDGE_KEY_FILE:-${HOME}/.config/zhiyun/snail-document-bridge.key}"
if [[ ! -e "$key_file" ]]; then
  umask 077
  mkdir -p "$(dirname "$key_file")"
  temporary_key="$(mktemp "${key_file}.XXXXXX")"
  trap 'rm -f "$temporary_key"' EXIT
  head -c 32 /dev/urandom > "$temporary_key"
  chmod 600 "$temporary_key"
  # 硬链接保证并发启动时只发布一个完整密钥。
  ln "$temporary_key" "$key_file" 2>/dev/null || [[ -f "$key_file" ]]
fi
if [[ ! -f "$key_file" || ! -r "$key_file" || "$(wc -c < "$key_file")" -ne 32 ]]; then
  echo '错误：智能体文件工具签名密钥不可用。' >&2
  exit 1
fi
