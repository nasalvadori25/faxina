# Faxina — encontra e apaga o que está pesando no celular

## O que o app faz
- Mostra quanto do armazenamento está usado e livre
- Varre todos os seus arquivos e soma por tipo: Vídeos, Fotos, Áudios, Documentos, APKs e WhatsApp
- Lista os maiores arquivos (acima de 1 MB), do maior para o menor
- Botão "Ver pastas": mostra as pastas mais pesadas; toque numa pasta para ver só os arquivos dela
- Filtros: Todos, Vídeos, Fotos, Áudios, Documentos, APKs, Outros, WhatsApp, Downloads
- Toque para marcar, "Marcar todos" para a lista visível, "Apagar" com confirmação
- Toque longo num arquivo para abrir (conferir o vídeo/foto antes de apagar)
- Não mexe em Android/data e Android/obb (dados internos dos apps)

## Gerar o APK pelo GitHub (sem instalar nada)
1. Crie um repositório novo (pode ser privado) em github.com
2. Envie todos os arquivos desta pasta (incluindo a pasta oculta .github)
   - Pelo site: "Add file" → "Upload files" e arraste a pasta descompactada
3. Abra a aba **Actions**. O workflow "Gerar APK" roda sozinho (uns 3–5 minutos)
4. Quando ficar verde, entre na execução e baixe **Faxina-apk** em "Artifacts"
5. Descompacte e mande o app-debug.apk para o celular

## Ou pelo Android Studio
Abra esta pasta no Android Studio, espere o Gradle sincronizar, conecte o celular
com depuração USB ativa e clique em Run ▶.

## Instalar no celular
1. Abra o .apk e permita "instalar apps desconhecidos" para o app que abriu o arquivo
2. Se o Play Protect avisar, toque em "Instalar mesmo assim" (o aviso aparece porque o app não veio da Play Store)
3. Na primeira vez que tocar em Escanear, ative "Permitir acesso para gerenciar todos os arquivos"

## Importante
- Apagar é definitivo: os arquivos não vão para a lixeira
- O espaço ocupado por apps e pelo sistema não aparece na lista. Para isso use o botão
  "Armaz. do sistema" (lá dá pra limpar cache e desinstalar apps)
