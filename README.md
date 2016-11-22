# akka-pdf

AKKA-PDF is pdf translation sample project under Apache2 License 

## dev

- activator

- pdfActor/run 2551 (you should select PdfWorker)

- Or activator "pdfActor/run 2551"

- playApp/run 80 (running port)

- Or activator "playApp/run 80"

## package & run

- activator

- assembly

- java -jar PdfActor-assembly-0.01.jar 2551

- java -jar -Dplay.crypto.secret=abcdefghijk PlayApp-assembly-0.01.jar
