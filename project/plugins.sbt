resolvers += Resolver.url(
  "tpolecat-sbt-plugin-releases",
    url("http://dl.bintray.com/content/tpolecat/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

addSbtPlugin("org.tpolecat"      % "tut-plugin"            % "0.4.7")
addSbtPlugin("com.eed3si9n"      % "sbt-unidoc"            % "0.3.3")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"               % "1.0.1")
addSbtPlugin("com.github.gseitz" % "sbt-release"           % "1.0.3")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype"          % "0.5.0")
addSbtPlugin("org.scalastyle"   %% "scalastyle-sbt-plugin" % "0.8.0")
addSbtPlugin("com.eed3si9n"      % "sbt-doge"              % "0.1.5")
addSbtPlugin("io.get-coursier"   % "sbt-coursier"          % "1.0.0-M14-7")
