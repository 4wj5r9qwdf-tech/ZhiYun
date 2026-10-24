import os, sys
root = sys.argv[1]
fixed = []
for dp, dn, fn in os.walk(root):
    dn[:] = [d for d in dn if d != ".git"]
    for f in fn:
        p = os.path.join(dp, f)
        if open(p,"rb").read(3) == b"\xef\xbb\xbf":
            data = open(p,"rb").read()
            open(p,"wb").write(data[3:])
            fixed.append(p)
print("去 BOM 文件数:", len(fixed))
for p in fixed: print("  ", p)
# 去掉指定目录下所有文本文件的 UTF-8 BOM（贴 Linux 语义）
import os, sys
root = sys.argv[1]
exts = {".md",".json",".sql",".ts",".js",".vue",".java",".xml",".yml",".yaml",".toml",".properties"}
fixed = []
for dp, dn, fn in os.walk(root):
    dn[:] = [d for d in dn if d != ".git"]
    for f in fn:
        p = os.path.join(dp, f)
        b = open(p, "rb").read(3)
        if b == b"\xef\xbb\xbf":
            data = open(p, "rb").read()
            open(p, "wb").write(data[3:])
            fixed.append(p)
print("去 BOM 文件数:", len(fixed))
for p in fixed: print("  ", p)
