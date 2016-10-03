curl -i -X POST -F "file0=@test1.pdf" -F "file1=@test2.pdf" http://127.0.0.1:9000/MemoryMergeToMemory

curl -i -X POST --data-urlencode "url=http://127.0.0.1:9000/a4" http://127.0.0.1:9000/WebToPdf