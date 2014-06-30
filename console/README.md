Utility scripts for use in the Play console. Unless otherwise noted, you
must be in the test console:

    [phm-server] $ test:console

Use `:load` or `:l` to load the scripts. For instance:

    scala> :l console/init

How to access application configuration:

    scala> app.configuration.getString("db.default.jndiName")
    res1: Option[String] = Some(DefaultDS)

How to create an encrypted password for a user. In this case, the plaintext
password is "kov4-quoyt", and it encrypts to
"4ee0ee429302ab77fa8a8902f90c73e916d7a2e9".

    scala> run(UserUtil.encryptPassword("kov4-quoyt"))
    [info] play - datasource [jdbc:mysql://localhost:3306/phmhealth] bound to JNDI as DefaultDS
    res2: String = 4ee0ee429302ab77fa8a8902f90c73e916d7a2e9
