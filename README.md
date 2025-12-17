# 📱 Suportvip SMS Connector

> **Sincronização Automática de SMS para Google Sheets (SaaS)**

O **Suportvip SMS Connector** é uma solução de engenharia de dados que captura mensagens SMS em tempo real de dispositivos Android e as estrutura automaticamente em Dashboards financeiros/operacionais no Google Sheets. O sistema opera de forma transparente, validando licenças e gerenciando permissões de acesso automaticamente.

---

## 🔄 Fluxo de Dados (Architecture Flow)

O sistema segue uma arquitetura **Event-Driven** (orientada a eventos), onde a chegada de um SMS dispara todo o processo:

1.  **Captura (Edge):** O App Android intercepta o SMS recebido (filtra SPAM via Regex local).
2.  **Transmissão (API):** O App envia um payload JSON seguro para o Google Apps Script (Serverless).
3.  **Validação (Auth):** O Script consulta a **Planilha Mestra**:
    * Valida o Token de Licença.
    * Verifica a Validade (Data) e Status (Ativo).
    * Realiza o *Device Bind* (vincula o token ao ID único do hardware).
4.  **Roteamento (Data Lake):** O Script localiza o ID da Planilha do Cliente específico.
5.  **Persistência (Write):**
    * Escreve os dados na aba oculta `DADOS_BRUTOS`.
    * Aplica formatação automática (largura, data, efeito zebra).
6.  **Auto-Onboarding (Share):** Se o e-mail do cliente ainda não tiver acesso, o Script compartilha a planilha automaticamente via Google Drive API.
7.  **Feedback:** O Android recebe o status (`Success/Error`) e notifica o usuário localmente.

---

## 🚀 Funcionalidades Principais

### 📱 Android App (Client)
* **Background Service:** Roda silenciosamente, mesmo com o app fechado (requer permissão de bateria).
* **Filtro Inteligente:** Ignora mensagens irrelevantes (promoções, operadora) usando Regex.
* **Notificações Locais:** Feedback visual de sucesso ou erro de sincronização.
* **Segurança:** Vinculação de Hardware (Token só funciona em 1 aparelho).

### ☁️ Backend (Google Apps Script)
* **Zero Infra:** Roda 100% na nuvem do Google (sem servidores VPS).
* **Gestão de Licenças:** Controle centralizado de vencimento e bloqueio de usuários.
* **Auto-Healing:** Tenta recuperar conexões e gerenciar erros de escrita.
* **Drive Automation:** Concede permissão de edição/leitura ao cliente sem intervenção manual.

---

## 🛠️ Instalação e Configuração (Admin)

### 1. Planilha Mestra (Database)
Crie uma planilha com a aba `USUARIOS_SMS` contendo as colunas:
* **A:** Email do Cliente (Google Account)
* **B:** Device ID (Preenchido automaticamente pelo sistema)
* **C:** Token (Gerado pelo menu Admin)
* **D:** Vencimento (Data)
* **E:** Status (`ATIVO` / `BLOQUEADO`)
* **F:** ID Planilha Cliente (ID do arquivo Google Sheets de destino)

### 2. Google Apps Script (API)
1.  Implante o código `doPost` como **App da Web**.
2.  **Executar como:** `Usuário implantando` (Sua conta Admin).
3.  **Quem pode acessar:** `Qualquer pessoa` (Anônimo).
4.  Configure o Manifesto (`appsscript.json`) com permissões de `Drive` e `Sheets`.

### 3. App Android
1.  No `NetworkLayer.kt`, insira a URL gerada pelo Apps Script.
2.  Compile o APK e instale no dispositivo do cliente.
3.  Garanta as permissões: *SMS, Notificações e Bateria Irrestrita*.

---

## 📊 Estrutura da Planilha do Cliente (Template)

Para garantir a integridade dos dados, entregamos ao cliente um arquivo com duas camadas:

* **Aba `DADOS_BRUTOS` (Oculta):** Onde o script escreve. Contém o histórico completo.
* **Aba `DASHBOARD` (Visível):** Interface visual com gráficos e tabelas estilizadas usando a função `=QUERY()` para ler os dados brutos em tempo real.

---

## 📝 Exemplo de JSON (Payload)

```json
{
  "license_key": "K9M4X2",
  "device_id": "android_f82...",
  "target_email": "cliente@gmail.com",
  "sms_content": "Compra aprovada R$ 100,00 LOJA X",
  "sender_number": "27900"
}

⚠️ Notas de Segurança
O Token é único e intransferível (Hardware Binding).

O sistema não envia e-mails (evita bloqueio de cota e spam). O acesso é concedido via notificação nativa do Google Drive.

Dados sensíveis trafegam via HTTPS diretamente para os servidores do Google.
