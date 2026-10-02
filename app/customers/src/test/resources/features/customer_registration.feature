# language-plugin não usado; palavras-chave em inglês, corpo em PT-BR — padrão dos demais .feature do repo
Feature: Cadastro do Customer (aggregate root do contexto Customers)

  A fonte de verdade de quem compra. Customer só é válido com pelo menos um
  Delivery Address, e o Document determina o Customer Type — nunca informado
  à parte (CONTEXT.md).

  Scenario: CPF com dígito verificador válido vira Document e determina INDIVIDUAL
    Given o documento "52998224725"
    When o documento é registrado
    Then ele vira um Document válido
    And o Customer Type derivado é "INDIVIDUAL"

  Scenario: CNPJ com dígito verificador válido vira Document e determina COMPANY
    Given o documento "11222333000181"
    When o documento é registrado
    Then ele vira um Document válido
    And o Customer Type derivado é "COMPANY"

  Scenario: CPF com dígito verificador inválido não é representável
    Given o documento "52998224720"
    When o documento é registrado
    Then o Document é recusado com motivo de dígito verificador

  Scenario: CNPJ com dígito verificador inválido não é representável
    Given o documento "11222333000180"
    When o documento é registrado
    Then o Document é recusado com motivo de dígito verificador

  Scenario: Documento com tamanho fora de CPF ou CNPJ é recusado
    Given o documento "12345"
    When o documento é registrado
    Then o Document é recusado com motivo de tamanho

  Scenario: CPF com todos os dígitos iguais é recusado
    Given o documento "11111111111"
    When o documento é registrado
    Then o Document é recusado com motivo de dígito verificador

  Scenario: Customer com primeiro Delivery Address o tem como Default
    Given um Customer "Distribuidora Horizonte" com documento "11222333000181"
    When o primeiro Delivery Address é registrado
    Then o Customer é válido
    And esse endereço é o Default Delivery Address

  Scenario: Customer sem nenhum Delivery Address não é válido
    Given um Customer "Maria Silva" com documento "52998224725"
    When tento criar o Customer sem nenhum Delivery Address
    Then a criação é recusada por ausência de Delivery Address

  Scenario: Segundo Delivery Address não muda o Default
    Given um Customer "Distribuidora Horizonte" com documento "11222333000181"
    And o primeiro Delivery Address foi registrado
    When um segundo Delivery Address é registrado
    Then o Customer tem 2 Delivery Addresses
    And o Default Delivery Address continua sendo o primeiro
