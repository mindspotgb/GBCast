# Validação local — 26/10/2026

Ambiente: Windows, SDK Android local, Java 17, Retroid Pocket Nova via ADB. Rodada inicial: GBCast debug build 36 instalado preservando dados, com testes executados localmente antes de qualquer envio ao GitHub.

## Builds e testes

- GBCast: `:gbcast:assembleDebug`, `:gbcast:testDebugUnitTest` e `:gbcast:lintDebug` concluídos com sucesso após a última alteração.
- GBCast: 18 testes unitários, sem falhas, cobrindo modos de vídeo, importação de BIOS, correspondência do catálogo de cheats, índice de jogos, navegação Back e validação de servidor RA.
- GBSat: `:app:assembleDebug` e os três testes `LibraryStartupTest` concluídos com sucesso usando o GBFrame local atualizado.
- GBFrame: seleção inicial distingue último jogo aberto da última capa apenas visitada. Migração de preferência antiga e fallback para jogo removido cobertos nos testes do consumidor.

## Conferido no aparelho

- Google Imagens aberto dentro do app: selecionar capa, prévia, aplicar e retornar à biblioteca usando Back.
- Textos selecionados nas configurações permanecem claros.
- Seletor único de vídeo nas configurações e menu rápido: 4:3, 16:9, Super widescreen, Fullscreen. As 16 transições entre modos foram verificadas no teste unitário.
- Sete controles de overlays presentes em Vídeo nas configurações principais e em Overlays no menu rápido. FPS renderizado pelo componente GBFrame.
- Menu rápido Game sem Auto save/Auto load; menu rápido Video sem Show FPS e Rotate.
- Alternância entre toque, direcional e A/B no menu rápido. A correção do foco também exige que o ComposeView Android dê preferência aos descendentes.
- Savestate criado no Slot 5 manual (índice 4), com miniatura visível na lista de carregamento.
- Auto save ativado temporariamente: arquivo `Daytona USA_5.state` criado ao deixar o app, separado dos slots manuais 0–4.
- Auto load corrigido e confirmado pelo registro nativo: `Loaded state ver 854 ... Daytona USA_5.state`. O carregamento explícito do slot automático preserva a seleção manual.
- Pausa preservada ao colocar o app em segundo plano e retornar com o menu aberto.
- Catálogo offline de cheats reconheceu Daytona USA (USA), acrescentou oito operações desativadas e recusou duplicação na segunda importação. Ativação/desativação conferida na interface; não foi validado o efeito de cada código durante uma corrida.
- Desenho do controle Dreamcast conferido com botões sobrepostos nas posições medidas.
- Jogo conferido com tela temporariamente quadrada (720×720), usando a apresentação alinhada ao topo. Tamanho e rotação originais restaurados.
- Navegação por outra capa e reabertura do app para o último jogo iniciado conferidas; regras de persistência cobertas nos testes automatizados.

## Limites da validação

- Os 294 arquivos de cheats estão incluídos; os códigos de todos os jogos não foram individualmente testados. Correspondência ambígua entre regiões/revisões não seleciona um arquivo automaticamente.
- A importação dos dois arquivos BIOS foi validada com arquivos temporários nos testes; firmware real não foi instalado no aparelho.
- Notificações de conquistas usam o componente GBFrame e o encaminhamento de eventos nativos; nenhum desbloqueio real de RA foi exercitado nesta sessão.
- O GBSat instalado no Nova é o build 353, anterior ao ajuste dos ícones. O código local usa o quadro padrão de 40 dp com margens transparentes removidas e foi conferido em renderização local. A assinatura local difere da instalada; não houve desinstalação nem perda de dados para substituí-la.
- A nova captura de miniaturas vale para novos savestates. Estados antigos sem imagem precisam ser salvos novamente.

As opções Auto save/Auto load foram devolvidas ao estado anterior, desativadas; cheats de teste ficaram desativados. A tela do aparelho foi apagada ao concluir o uso.

## Complemento: transparência e sincronização dos repositórios

- GBCast build 38: build, 18 testes unitários e lint concluídos localmente; APK instalado preservando dados.
- O launcher do Nova foi conferido com o GBCast selecionado sobre um fundo cinza: o controle aparece sem placa preta. O recurso transparente raster é utilizado tanto em `android:icon` quanto em `android:roundIcon`; a variante adaptativa opaca é apenas um recurso explícito de compatibilidade.
- O GBFrame não aplica moldura nem recorte aos ícones internos e sua regra de identidade foi atualizada.
- GBSat: build local e testes de recursos de ícone, persistência da seleção e renderização do menu quadrado passaram. A prévia mostra o controle sem moldura. A atualização da instalação oficial depende do APK assinado pelo processo existente no GitHub, pois a senha da chave não está disponível no ambiente local.
- Após a validação local, o usuário solicitou a atualização dos três repositórios. As mudanças de código e os padrões compartilhados foram preparados para sincronização; o resultado do build assinado e a instalação devem ser confirmados separadamente.

Confirmação posterior: os três repositórios foram sincronizados. O [build oficial 374 do GBSat](https://github.com/mindspotgb/GBSat/actions/runs/40434824290), referente ao commit `abd9a9e1cbfbf836c29ce9a2bdb671c5783d6492`, terminou com sucesso e gerou o APK. A assinatura foi verificada como idêntica à instalação 353 antes de atualizar o Nova com `install -r`. O aparelho confirmou o build 374; menu e About foram inspecionados sem a moldura externa, mantendo o contorno da silhueta do controle. Nenhuma desinstalação foi necessária. As imagens preexistentes alteradas em `GBSat/docs/screenshots` foram preservadas localmente fora dos commits.
