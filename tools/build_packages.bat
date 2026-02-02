@echo off
echo Building Linux Package (.deb)...
docker run --rm -v "%cd%:/data" -w /data gcc:13-bookworm sh -c "cp -r packaging/linux /tmp/pkg && chmod -R 755 /tmp/pkg && dpkg-deb --build /tmp/pkg /data/packaging/linux/somnia_2.0.0_amd64.deb"

echo Building Windows Executable (.exe)...
echo This may take a few minutes to install MinGW in Docker...
docker run --rm -v "%cd%:/data" -w /data gcc:13-bookworm sh -c "apt-get update >/dev/null && apt-get install -y mingw-w64 >/dev/null && x86_64-w64-mingw32-gcc -O3 -std=gnu99 -DSOMNIA_NO_SQL -I./somnia-native/include ./somnia-native/src/main.c ./somnia-native/src/interpreter.c ./somnia-native/src/lexer.c ./somnia-native/src/parser.c ./somnia-native/src/value.c ./somnia-native/src/stdlib.c ./somnia-native/src/network.c ./somnia-native/src/env.c ./somnia-native/src/sql.c -o packaging/windows/somnia_engine.exe -lm -lws2_32"

echo Build Complete.
echo Linux: packaging\linux\somnia_2.0.0_amd64.deb
echo Windows: packaging\windows\somnia_engine.exe
pause
