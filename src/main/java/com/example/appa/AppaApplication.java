package com.example.appa;

import com.github.braully.boleto.LayoutsSuportados;
import com.github.braully.boleto.RemessaArquivo;
import com.rabbitmq.client.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.io.*;


@SpringBootApplication
@RestController
public class AppaApplication {


	private final static String QUEUE_NAME = "EnviarRemessa";

	public static void main(String[] args) {
		SpringApplication.run(AppaApplication.class, args);
	}
    private CompletableFuture<String> mensagemFuture = new CompletableFuture<>();
	@PostMapping(value = "/GerarRemessas")
	public String postGerarRemessas(int QtdRemessas){
		String caminhoPlanilha = "C:/Users/Pichau/Desktop/BoletosNaoPagos/Clientes.xlsm";

		try (FileInputStream fis = new FileInputStream(caminhoPlanilha)) {
			Workbook workbook = new XSSFWorkbook(fis);
			Sheet sheet = workbook.getSheetAt(0); // Obtém a primeira planilha


			// Itera pelas linhas da planilha, começando da segunda linha (índice 1)
			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);


				// Extrai os dados da planilha
				String nome = row.getCell(0).getStringCellValue();
				Double valor =  row.getCell(1).getNumericCellValue();
				String cpf = row.getCell(2).getStringCellValue();
				Integer matricula = (int) row.getCell(3).getNumericCellValue();
				Date dataEmissao = row.getCell(4).getDateCellValue();

				RemessaArquivo remessa = new RemessaArquivo(LayoutsSuportados.LAYOUT_BB_CNAB240_COBRANCA_REMESSA);
				remessa.addNovoCabecalho()
						.sequencialArquivo(1)
						.dataGeracao(new Date())
						.setVal("horaGeracao", new Date())
						.banco("0", "Banco")
						.cedente("ACME S.A LTDA.", "1")
						.convenio("1", "1", "1", "1")
						.carteira("00");


				// Gera a remessa com os dados extraídos
				remessa.addNovoDetalheSegmentoP()
						.valor(valor)
						.dataGeracao(new Date())
						.dataVencimento(dataEmissao)
						.numeroDocumento(rowIndex)
						.nossoNumero(rowIndex)
						.banco("0", "Banco")
						.cedente("ACME S.A LTDA.", "1")
						.convenio("1", "1", "1", "1")
						.sequencialRegistro(rowIndex)
						.carteira("00");

				remessa.addNovoDetalheSegmentoQ()
						.sacado(nome, cpf)
						.banco("0", "Banco")
						.cedente("ACME S.A LTDA.", "1")
						.convenio("1", "1", "1", "1")
						.sequencialRegistro(rowIndex + 1)
						.carteira("00");

				remessa.addNovoRodapeLote()
						.quantidadeRegistros(sheet.getLastRowNum())
						.valorTotalRegistros(1)
						.banco("0", "Banco")
						.cedente("ACME S.A LTDA.", "1")
						.convenio("1", "1", "1", "1")
						.carteira("00");

				remessa.addNovoRodape()
						.quantidadeRegistros(sheet.getLastRowNum())
						.valorTotalRegistros(1)
						.setVal("codigoRetorno", "1")
						.banco("0", "Banco").cedente("ACME S.A LTDA.", "1")
						.convenio("1", "1", "1", "1")
						.carteira("00");


				String remessaStr = remessa.render();
				String nRemessa = "remessa" + rowIndex +".txt";

				EnviarMensagemDaRemessa(nRemessa);
				try (FileWriter writer = new FileWriter("C:/Users/Pichau/source/repos/Pagamentos/Pagamentos/BoletoBancario/" + nRemessa)) {
					writer.write(remessaStr);
				}

				// Verifica se o contador atingiu mil e interrompe o loop
				if (rowIndex == QtdRemessas) {
					break;
				}
			}



			// Feche o workbook após a conclusão do processamento
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		}
		return "Remessas Geradas";
	}

	public static void EnviarMensagemDaRemessa(String nRemessa) throws IOException, TimeoutException {
		// Configuração da conexão com o servidor RabbitMQ
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost"); // Altere para o IP do seu servidor RabbitMQ
		factory.setPort(32790); // Porta padrão do RabbitMQ

		Connection connection = null;
		Channel channel = null;
		try {
			// Cria uma conexão e um canal
			connection = factory.newConnection();
			channel = connection.createChannel();

			// Declara a fila
			channel.queueDeclare(QUEUE_NAME, false, false, false, null);

			String mensagem = "[x] Remessa recebida para importação: " + nRemessa + "!" ;
			for (int i = 0; i < 1; i++) {
				String mensagemCompleta = mensagem;

				channel.basicPublish("", QUEUE_NAME, null, mensagemCompleta.getBytes("UTF-8"));
				System.out.println("Mensagem enviada: " + mensagemCompleta);
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		} finally {
			// Fecha o canal e a conexão
			if (channel != null) {
				channel.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
	}

}
