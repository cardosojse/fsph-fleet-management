
import { useState } from 'react'
import './App.css'

const veiculosIniciais = [
  { id: 1, placa: 'QMA-1A23', modelo: 'Onix', marca: 'Chevrolet', ano: 2023, status: 'Disponível' },
  { id: 2, placa: 'QMA-4B56', modelo: 'Corolla', marca: 'Toyota', ano: 2022, status: 'Em uso' },
  { id: 3, placa: 'QMA-7C89', modelo: 'HB20', marca: 'Hyundai', ano: 2024, status: 'Manutenção' },
  { id: 4, placa: 'QMA-2D45', modelo: 'Strada', marca: 'Fiat', ano: 2023, status: 'Disponível' },
]

function App() {
  const [veiculos, setVeiculos] = useState(veiculosIniciais)
  const [pesquisa, setPesquisa] = useState('')
  const [filtro, setFiltro] = useState('Todos')
  const [mostrarFormulario, setMostrarFormulario] = useState(false)
  const [novoVeiculo, setNovoVeiculo] = useState({
    placa: '',
    modelo: '',
    marca: '',
    ano: '',
  })

  const veiculosFiltrados = veiculos.filter((veiculo) => {
    const correspondePesquisa =
      `${veiculo.placa} ${veiculo.modelo} ${veiculo.marca}`
        .toLowerCase()
        .includes(pesquisa.toLowerCase())

    const correspondeStatus =
      filtro === 'Todos' || veiculo.status === filtro

    return correspondePesquisa && correspondeStatus
  })

  function cadastrarVeiculo(event) {
    event.preventDefault()

    const veiculo = {
      ...novoVeiculo,
      id: Date.now(),
      ano: Number(novoVeiculo.ano),
      status: 'Disponível',
    }

    setVeiculos([...veiculos, veiculo])
    setNovoVeiculo({ placa: '', modelo: '', marca: '', ano: '' })
    setMostrarFormulario(false)
  }

  return (
    <div className="aplicacao">
      <aside className="menu">
        <div className="marca">
          <div className="marca-icone">F</div>
          <div>
            <h2>FSPH</h2>
            <span>Gestão de Frota</span>
          </div>
        </div>

        <p className="menu-titulo">MENU PRINCIPAL</p>

        <nav>
          <a href="#inicio">⌂ <span>Início</span></a>
          <a href="#veiculos" className="ativo">▣ <span>Veículos</span></a>
          <a href="#motoristas">♙ <span>Motoristas</span></a>
          <a href="#reservas">▤ <span>Reservas</span></a>
        </nav>

        <div className="menu-rodape">
          <span className="avatar">M</span>
          <div>
            <strong>Moara</strong>
            <small>Desenvolvedora</small>
          </div>
        </div>
      </aside>

      <main className="conteudo" id="veiculos">
        <header className="cabecalho">
          <div>
            <span className="caminho">Gestão de frota / Veículos</span>
            <h1>Veículos</h1>
            <p>Consulte e gerencie os veículos da frota.</p>
          </div>
          <div className="usuario">M</div>
        </header>

        <section className="resumo">
          <article className="cartao">
            <span>Total de veículos</span>
            <strong>{veiculos.length}</strong>
            <small>Veículos cadastrados</small>
          </article>
          <article className="cartao">
            <span>Disponíveis</span>
            <strong>{veiculos.filter(v => v.status === 'Disponível').length}</strong>
            <small>Prontos para uso</small>
          </article>
          <article className="cartao">
            <span>Em uso</span>
            <strong>{veiculos.filter(v => v.status === 'Em uso').length}</strong>
            <small>Em operação</small>
          </article>
          <article className="cartao">
            <span>Manutenção</span>
            <strong>{veiculos.filter(v => v.status === 'Manutenção').length}</strong>
            <small>Precisam de atenção</small>
          </article>
        </section>

        <section className="painel">
          <div className="painel-topo">
            <div>
              <h2>Lista de veículos</h2>
              <p>Visualize os dados cadastrados na frota.</p>
            </div>
            <button className="botao-principal" onClick={() => setMostrarFormulario(!mostrarFormulario)}>
              + Novo veículo
            </button>
          </div>

          {mostrarFormulario && (
            <form className="formulario" onSubmit={cadastrarVeiculo}>
              <h3>Cadastrar veículo</h3>
              <div className="campos">
                <label>
                  Placa
                  <input
                    required
                    value={novoVeiculo.placa}
                    placeholder="ABC-1234"
                    onChange={e => setNovoVeiculo({ ...novoVeiculo, placa: e.target.value.toUpperCase() })}
                  />
                </label>
                <label>
                  Modelo
                  <input
                    required
                    value={novoVeiculo.modelo}
                    placeholder="Modelo do veículo"
                    onChange={e => setNovoVeiculo({ ...novoVeiculo, modelo: e.target.value })}
                  />
                </label>
                <label>
                  Marca
                  <input
                    required
                    value={novoVeiculo.marca}
                    placeholder="Marca"
                    onChange={e => setNovoVeiculo({ ...novoVeiculo, marca: e.target.value })}
                  />
                </label>
                <label>
                  Ano
                  <input
                    required
                    type="number"
                    min="1900"
                    max="2100"
                    value={novoVeiculo.ano}
                    placeholder="2025"
                    onChange={e => setNovoVeiculo({ ...novoVeiculo, ano: e.target.value })}
                  />
                </label>
              </div>
              <div className="acoes-formulario">
                <button type="button" className="botao-secundario" onClick={() => setMostrarFormulario(false)}>Cancelar</button>
                <button type="submit" className="botao-principal">Salvar veículo</button>
              </div>
            </form>
          )}

          <div className="filtros">
            <input
              className="pesquisa"
              value={pesquisa}
              onChange={e => setPesquisa(e.target.value)}
              placeholder="⌕  Pesquisar por placa, modelo ou marca..."
            />
            <select value={filtro} onChange={e => setFiltro(e.target.value)}>
              <option>Todos</option>
              <option>Disponível</option>
              <option>Em uso</option>
              <option>Manutenção</option>
            </select>
          </div>

          <div className="tabela-container">
            <table>
              <thead>
                <tr>
                  <th>PLACA</th>
                  <th>VEÍCULO</th>
                  <th>ANO</th>
                  <th>STATUS</th>
                </tr>
              </thead>
              <tbody>
                {veiculosFiltrados.map(veiculo => (
                  <tr key={veiculo.id}>
                    <td><strong>{veiculo.placa}</strong></td>
                    <td>
                      <div className="veiculo-info">
                        <span className="veiculo-icone">▣</span>
                        <div>
                          <strong>{veiculo.modelo}</strong>
                          <small>{veiculo.marca}</small>
                        </div>
                      </div>
                    </td>
                    <td>{veiculo.ano}</td>
                    <td>
                      <span className={`status ${veiculo.status.toLowerCase().replace(' ', '-')}`}>
                        {veiculo.status}
                      </span>
                    </td>
                  </tr>
                ))}
                {veiculosFiltrados.length === 0 && (
                  <tr>
                    <td colSpan="4" className="vazio">Nenhum veículo encontrado.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="rodape-tabela">
            Exibindo {veiculosFiltrados.length} de {veiculos.length} veículos
          </div>
        </section>

        <footer>FSPH — Sistema de Gestão de Frota</footer>
      </main>
    </div>
  )
}

export default App
