package br.com.segware;

import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.opencsv.CSVReader;

public class AnalisadorRelatorio implements IAnalisadorRelatorio {

	private static final int POS_COD_SEQUENCIAL = 0;
	private static final int POS_COD_CLIENTE = 1;
//	private static final int POS_COD_EVENTO = 2;
	private static final int POS_TIPO_EVENTO = 3;
	private static final int POS_DATA_INICIAL = 4;
	private static final int POS_DATA_FINAL = 5;
	private static final int POS_COD_ATENDENTE = 6;
	private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

	@Override
	public Map<String, Integer> getTotalEventosCliente() {
		List<String[]> lines = csvReader();
		Map<String, Integer> totalEventosClienteMap = new HashMap<String, Integer>();
		for (String[] line : lines) {
			String codCliente = line[POS_COD_CLIENTE];
			if (totalEventosClienteMap.containsKey(codCliente)) {
				Integer totalEventos = totalEventosClienteMap.get(codCliente);
				totalEventos++;
				totalEventosClienteMap.put(codCliente, totalEventos);
			} else {
				totalEventosClienteMap.put(codCliente, 1);
			}
		}

		return totalEventosClienteMap;
	}

	@Override
	public Map<String, Long> getTempoMedioAtendimentoAtendente() {
		List<String[]> lines = csvReader();

		Map<String, Integer> totalAtendimentos = new HashMap<String, Integer>();
		Map<String, Long> totalTempos = new HashMap<String, Long>();

		for (String[] line : lines) {
			String codAtendente = line[POS_COD_ATENDENTE];
			calculaTempoAtendimento(totalTempos, line, codAtendente);
			calculaAtendimentos(totalAtendimentos, codAtendente);
		}

		Map<String, Long> tempoMedioAtendimento = calculaTempoMedio(totalAtendimentos, totalTempos);

		return tempoMedioAtendimento;
	}

	private Map<String, Long> calculaTempoMedio(Map<String, Integer> totalAtendimentos, Map<String, Long> totalTempos) {
		Map<String, Long> tempoMedioAtendimento = new HashMap<String, Long>();

		Set<Entry<String, Integer>> entrySet = totalAtendimentos.entrySet();

		for (Entry<String, Integer> entry : entrySet) {
			String codigoAtendente = entry.getKey();
			Integer totalAtendimento = entry.getValue();
			Long tempoTotal = totalTempos.get(codigoAtendente);
			Long tempoMedio = tempoTotal / totalAtendimento;
			tempoMedioAtendimento.put(codigoAtendente, tempoMedio);
		}
		return tempoMedioAtendimento;
	}

	private void calculaAtendimentos(Map<String, Integer> totalAtendimentos, String codAtendente) {
		if (totalAtendimentos.containsKey(codAtendente)) {
			int atendimentos = totalAtendimentos.get(codAtendente);
			totalAtendimentos.put(codAtendente, ++atendimentos);
		} else {
			totalAtendimentos.put(codAtendente, 1);
		}
	}

	private void calculaTempoAtendimento(Map<String, Long> totalTempos, String[] line, String codAtendente) {
		DateTime tempoInicial = FORMATTER.parseDateTime(line[POS_DATA_INICIAL]);
		DateTime tempoFinal = FORMATTER.parseDateTime(line[POS_DATA_FINAL]);
		Long duracao = (long) Seconds.secondsBetween(tempoInicial, tempoFinal).getSeconds();
		if (totalTempos.containsKey(codAtendente)) {
			Long totalDuracao = totalTempos.get(codAtendente);
			totalTempos.put(codAtendente, totalDuracao + duracao);
		} else {
			totalTempos.put(codAtendente, duracao);
		}
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public List<Tipo> getTiposOrdenadosNumerosEventosDecrescente() {

		List<String[]> lines = csvReader();
		List<Tipo> tipos = new ArrayList<Tipo>();
		Map<Tipo, Integer> tipoTemp = new HashMap<Tipo, Integer>();

		for (String[] line : lines) {
			Tipo atual = Tipo.valueOf(line[POS_TIPO_EVENTO]);
			somaTipoEvento(tipoTemp, atual);
		}

		Object[] a = ordenaTipoPorTotalDeEventos(tipoTemp);

		for (Object object : a) {
			Entry<Tipo, Integer> entry = (Entry<Tipo, Integer>) object;
			tipos.add(entry.getKey());
		}

		return tipos;
	}

	private void somaTipoEvento(Map<Tipo, Integer> tipoTemp, Tipo atual) {
		if (tipoTemp.containsKey(atual)) {
			int total = tipoTemp.get(atual);
			tipoTemp.put(atual, ++total);
		} else {
			tipoTemp.put(atual, 1);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object[] ordenaTipoPorTotalDeEventos(Map<Tipo, Integer> tipoTemp) {
		Object[] a = tipoTemp.entrySet().toArray();
		Arrays.sort(a, new Comparator() {

			public int compare(Object o1, Object o2) {
				return ((Map.Entry<Tipo, Integer>) o2).getValue().compareTo(((Map.Entry<Tipo, Integer>) o1).getValue());
			}
		});
		return a;
	}

	@Override
	public List<Integer> getCodigoSequencialEventosDesarmeAposAlarme() {

		List<Integer> codigos = new ArrayList<Integer>();
		List<String[]> lines = csvReader();
		boolean ocorreuAlarme = false;
		DateTime tempoAlarme = null;
		String codCliente = null;

		for (String[] line : lines) {
			Tipo tipo = Tipo.valueOf(line[POS_TIPO_EVENTO]);
			if (tipo.equals(Tipo.ALARME)) {
				tempoAlarme = FORMATTER.parseDateTime(line[POS_DATA_INICIAL]);
				ocorreuAlarme = true;
				codCliente = line[POS_COD_CLIENTE];
			}
			if (tipo.equals(Tipo.DESARME) && ocorreuAlarme && codCliente.equals(line[1])) {
				ocorreuAlarme = false;
				int codigo = Integer.valueOf(line[POS_COD_SEQUENCIAL]);
				DateTime tempoDesarme = FORMATTER.parseDateTime(line[POS_DATA_INICIAL]);
				int duracao = Minutes.minutesBetween(tempoAlarme, tempoDesarme).getMinutes();
				if (duracao < 5) {
					codigos.add(codigo);
				}
			}
		}

		return codigos;
	}

	public List<String[]> csvReader() {
		
		URL csvURL = getClass().getClassLoader().getResource("relatorio.csv");

		String csvFilePath = csvURL.toString().substring(5);

		CSVReader reader = null;
		List<String[]> lines = new ArrayList<String[]>();

		try {
			reader = new CSVReader(new FileReader(csvFilePath));
			String[] line;
			while ((line = reader.readNext()) != null) {
				lines.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return lines;
	}

}
