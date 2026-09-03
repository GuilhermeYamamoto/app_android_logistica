# App Logistica

Aplicativo Android que apresenta o sistema web de logística em um `WebView`.
O endereço carregado pelo aplicativo e o fluxo de autenticação pertencem ao
sistema web:

`https://serp-app.indufix.com.br/login`

## Estrutura do projeto

| Caminho | Responsabilidade |
|---|---|
| `app/src/main/java/com/example/applogistica/MainActivity.kt` | Cria e configura o `WebView`, trata permissões do Android, câmera, galeria e navegação de volta. |
| `app/src/main/AndroidManifest.xml` | Declara permissões e componentes Android, incluindo o `FileProvider` da câmera. |
| `app/src/main/res/xml/file_paths.xml` | Define o diretório de cache que pode ser compartilhado temporariamente com o aplicativo de câmera. |
| `app/src/main/res/values/strings.xml` | Textos apresentados pelo aplicativo. |
| `app/build.gradle.kts` | Configuração e dependências do módulo Android. |

## Como funciona a captura de fotos

O site exibido pelo `WebView` usa campos HTML para anexar imagens. No Android,
esses campos não abrem a câmera automaticamente: o aplicativo nativo precisa
intermediar a solicitação e devolver a imagem ao navegador embutido.

O fluxo implementado é:

1. O usuário toca no campo de foto do sistema web.
2. O `WebView` chama `onShowFileChooser` em `MainActivity`.
3. O aplicativo mostra o seletor Android **Selecionar foto**, com as opções de
   câmera e galeria.
4. Se a câmera for escolhida, uma imagem temporária é criada no cache do app e
   seu URI é compartilhado com segurança pelo `FileProvider`.
5. Depois da captura ou escolha na galeria, o URI da imagem é devolvido ao
   campo HTML que fez a solicitação.
6. O sistema web recebe o arquivo e pode enviá-lo normalmente.

O seletor também respeita campos que permitem mais de uma imagem e devolve
todas as imagens selecionadas.

## Correção: câmera não abria

### Problema identificado

`MainActivity` já configurava permissões de câmera e microfone para páginas que
usam WebRTC, mas não implementava `WebChromeClient.onShowFileChooser`.

Esse callback é obrigatório para campos HTML como `input type="file"` usados
para anexar ou capturar fotos no `WebView`. Sem ele, o toque no campo de foto
não tinha uma atividade Android para abrir; por isso a câmera e a galeria não
eram exibidas e não era possível anexar imagens.

Além disso, uma atividade externa de câmera não pode gravar diretamente em um
arquivo do app sem receber um URI compartilhável. A ausência de um
`FileProvider` impediria uma implementação segura desse fluxo em versões
modernas do Android.

### Solução aplicada

1. Foi implementado `onShowFileChooser` no `WebChromeClient`.
   - O callback recebido do `WebView` é guardado até o usuário concluir ou
     cancelar a seleção.
   - Uma solicitação anterior pendente é cancelada antes de abrir outra.

2. Foi criado um seletor nativo com câmera e galeria.
   - A galeria usa `ACTION_GET_CONTENT`, limitada a imagens.
   - A câmera usa `MediaStore.ACTION_IMAGE_CAPTURE`.
   - Caso não exista aplicativo de câmera instalado, o seletor continua
     oferecendo a galeria.

3. Foi configurado o `androidx.core.content.FileProvider` no manifesto.
   - Ele utiliza a autoridade
     `com.example.applogistica.fileprovider`.
   - O arquivo temporário da câmera fica no `cacheDir`, conforme
     `res/xml/file_paths.xml`.
   - A câmera recebe permissões temporárias de leitura e escrita somente para
     o URI criado.

4. Foi implementado o retorno do resultado em `onActivityResult`.
   - Fotos da galeria, múltiplas fotos e fotos capturadas pela câmera são
     convertidas em URIs e entregues ao `WebView`.
   - Ao finalizar, o callback e o URI temporário são limpos e as permissões de
     compartilhamento da câmera são revogadas.

5. O tratamento de permissões WebRTC foi ajustado.
   - Para solicitações de vídeo, o app pede `CAMERA`.
   - Para solicitações de áudio, o app pede `RECORD_AUDIO`.
   - Após a resposta do usuário, apenas os recursos de câmera e áudio pedidos
     pela página são concedidos ao `WebView`; a página não é mais recarregada.

## Permissões

| Permissão | Uso |
|---|---|
| `INTERNET` | Carregar o sistema web. |
| `CAMERA` | Permitir captura de vídeo/foto quando solicitada pelo sistema web. |
| `RECORD_AUDIO` | Permitir áudio somente quando o sistema web solicitar captura de áudio. |

## Observações de manutenção

- Não remova `onShowFileChooser`, o `FileProvider` ou `res/xml/file_paths.xml`;
  os três componentes são necessários para anexar fotos pelo `WebView`.
- O `WebView` não usa o cache HTTP e limpa o cache ao iniciar. Ao retornar ao
  aplicativo, a página é recarregada para buscar a versão mais recente do
  sistema web. Esse recarregamento é suspenso enquanto o seletor de imagens ou
  uma solicitação de permissão estiverem abertos.
- Ao mudar o `applicationId`, atualize a autoridade do `FileProvider` para
  continuar usando `${applicationId}.fileprovider`.
- A foto capturada fica no cache do aplicativo e é destinada ao envio imediato
  pelo sistema web. Ela não é salva automaticamente na galeria do dispositivo.

## Atualização da interface no tablet

O aplicativo apresenta o sistema web em um `WebView`. Para evitar que o tablet
continue mostrando uma versão antiga da interface, o aplicativo está
configurado para:

1. Ignorar o cache HTTP ao carregar páginas.
2. Limpar o cache do `WebView` ao iniciar.
3. Recarregar a página ao retornar ao aplicativo, por exemplo após deixá-lo em
   segundo plano.

O recarregamento automático não ocorre enquanto o seletor de imagens ou uma
solicitação de permissão estiverem abertos, evitando a perda de anexos e de
ações em andamento.

### Publicar a atualização no tablet

Depois de alterar o código, é preciso gerar e instalar uma nova versão do APK:

1. Abra a pasta `AppLogistica` no Android Studio.
2. Gere o APK em **Build > Build APK(s)**. Para distribuição, use
   **Build > Generate Signed Bundle / APK** e assine o APK de release.
3. Copie o arquivo APK gerado para o tablet.
4. Instale-o no tablet, substituindo a versão anterior. Se necessário, habilite
   a instalação de aplicativos dessa origem nas configurações do Android.

Para atualizar uma instalação existente sem removê-la, mantenha o mesmo
`applicationId` e assine o APK com a mesma chave usada na versão já instalada.
