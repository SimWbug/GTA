# GtaMC — mini-jeu GTA (1997) pour Paper 26.3

Police contre voleurs en voiture, vu de dessus comme le premier Grand Theft Auto.

## Build
Pousse le dossier sur GitHub : le workflow `.github/workflows/build.yml` compile le plugin
(Java 25, `paper-api 26.3`) et produit `GtaMC-1.0.0.jar` + `GtaMC-ResourcePack.zip` dans
l'onglet **Actions → Artifacts**. En local : `gradle build`.

## Mise en place
1. Copie le `.jar` dans `plugins/` et redémarre.
2. Resource pack : fusionne le dossier `resourcepack/assets/gta` dans ton pack serveur,
   **ou** héberge `GtaMC-ResourcePack.zip` et renseigne `resource-pack.url` / `sha1` dans `config.yml`
   avec `send-on-join: true`. Sans pack, mets `use-custom-models: false` (voitures en blocs de béton).
3. Place-toi à l'endroit voulu (sol plat, de préférence un monde dédié) et lance
   `/gta create liberty`. La ville 128x128 se construit vers l'est (+X) et le sud (+Z),
   en quelques secondes ; le bloc sous tes pieds devient le niveau de la route.

## Commandes
| Commande | Rôle |
|---|---|
| `/gta join [ville]` | rejoindre (départ auto à 2 joueurs, 20 s de compte à rebours) |
| `/gta leave` | quitter |
| `/gta wand <nom> [liberty\|vice\|sanandreas] [seed]` | bâton de placement : contour en particules, clic droit ×2 pour poser la ville centrée sur le bloc visé |
| `/gta create <nom> [thème] [seed]` | générer une ville à tes pieds (admin) |
| `/gta regen <nom>` | reconstruire la ville à l'identique (même seed) |
| `/gta setlobby / tp / start / stop / delete <nom>` | gestion |
| `/gta admin` | menu des réglages : ratio police, joueurs min/max, durée, butin, avance des voleurs |
| `/gta stats [joueur]` · `/gta top [victoires\|arrestations\|butin\|record\|parties]` | statistiques et top 10 |
| `/gta reload` | recharger `config.yml` |

## Règles
- **Équipes** : 1 policier pour 3 joueurs (2-5 → 1 flic, 6-8 → 2, 9-11 → 3, 12 → 4), 12 joueurs max. Réglable dans `/gta admin`.
- **Voleurs** : ramasser les paquets de billets qui apparaissent sur les routes, parkings et places.
  Le butin est commun à l'équipe ; objectif = `target-base + target-per-thief × voleurs`.
- **Police** : toucher un voleur avec sa voiture = arrestation immédiate (one shot).
  Gagne si tous les voleurs sont arrêtés ou si le temps s'écoule.
- Les voleurs ont 5 s d'avance au départ. Les arbres cachent les voitures vues du ciel.

## Tester seul avec des bots (admin)
- `/gta test` : rejoint la ville, ajoute 3 bots et lance la partie dans 3 s.
- `/gta test 5 police` / `/gta test 3 voleur` : choisit le nombre de bots et **ton** équipe.
- `/gta test 11 liberty` : partie complète à 12 dans la ville « liberty ».
- `/gta bot add <nb> [police|voleur]`, `/gta bot remove [nb]`, `/gta bot clear` : gérer les bots du lobby.
- Dans `/gta admin` : boutons « Tester en policier », « Tester en voleur », « Test complet (11 bots) ».

Les bots conduisent seuls sur les routes (calcul de chemin sur le plan de la ville) :
les voleurs ramassent les billets, braquent le convoi, libèrent leurs complices et fuient la police ;
les policiers prennent en chasse le voleur le plus proche en anticipant sa trajectoire et utilisent la nitro.
Ils portent « [BOT] » devant leur nom, ne comptent pas dans les statistiques, et leur vitesse se règle
avec `bots.speed` (0.9 par défaut). Ils disparaissent quand le dernier joueur quitte la partie.

## Prison et libération
Un voleur arrêté part en cellule devant le QG de police (son mannequin y est visible, sa vue passe
au-dessus de la cellule). Un voleur libre qui roule dans le cercle de la cellule libère tous ses
complices : ils réapparaissent à la planque, intouchables 3 s (`prison.protection`). La police gagne
si tous les voleurs sont en cellule en même temps.

## Événements en cours de partie (`events.*`)
- **Convoi de fonds** : un fourgon blindé traverse la ville ; un voleur qui le percute fait tomber $1000 en 4 paquets.
- **Coupure de courant** : nuit noire 20 s, seules les voitures et les billets restent éclairés.
- **Double butin** : billets doublés et apparitions deux fois plus rapides.
- **Alerte générale** : la police roule 15 % plus vite.

## Retour au lobby et vote
Après la cinématique, tout le monde revient au point d'attente (objet « Quitter » en 9e case).
S'il existe plusieurs villes, un vote de 15 s s'ouvre (menu, objet carte ou `/gta vote`) ;
le groupe est ensuite transféré dans la ville gagnante et la partie suivante se lance.

## Objet dans le hub (`hub.*`)
Dans les mondes listés dans `hub.worlds`, chaque joueur reçoit l'objet GTA en case `hub.slot`
(6e case par défaut, à côté de Loup-Garou en 4e et Find The Poulet en 5e). Clic droit = menu des villes
avec leur état et le nombre de joueurs. `/gta hubitem` le donne manuellement.

## Cinématique de fin
Après l'écran « VICTOIRE », la caméra de tous les joueurs passe sur l'équipe gagnante, alignée sur
l'avenue devant le QG (police) ou la planque (voleurs), chaque joueur (mannequin avec son vrai skin) devant sa voiture,
qui saute et lève les poings. Gyrophares pour la police, pluie de billets pour les voleurs, feux d'artifice, MVP
en sous-titre. Réglages : `game.end-cinematic` et `game.cinematic-duration`.

## Thèmes de ville
- **Liberty City** : briques, gris, parcs verts et chênes.
- **Vice City** : façades pastel, trottoirs blancs, parcs de sable, palmiers, piscine.
- **San Andreas** : terracotta, toits rouges, asphalte noir, acacias.

## Sons (resource pack)
Moteur dont le régime suit la vitesse, sirène de police (Sneak pour la couper / rallumer),
klaxon des voleurs (Sneak), « cha-ching » des billets, jingle *Busted* à l'arrestation.

## Contrôles (sans mod)
Le joueur est en spectateur, attaché à une caméra au-dessus de sa voiture (nord toujours en haut).
`Z/W` accélérer · `S` freiner puis reculer · `Q/D` tourner · `Espace` nitro · `Sneak` klaxon (voleurs) / sirène on-off (police).
La barre d'action affiche une flèche vers le paquet de billets le plus proche (voleurs) ou le voleur le plus proche (police), avec la distance.
La caméra recule quand on va vite, comme dans GTA 1.

## Structure
- `city/CityLayout` : plan de la ville depuis une seed (avenues, ruelles, parc, parking, QG police, planque).
- `city/CityBuilder` : pose des blocs par lots (`generation.columns-per-tick`).
- `game/Game` : cycle de partie, billets, arrestations, HUD (bossbar, sidebar, action bar).
- `game/Car` : physique, collisions avec les bâtiments, caméra.
